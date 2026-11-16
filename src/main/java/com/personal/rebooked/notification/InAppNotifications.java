package com.personal.rebooked.notification;

import com.personal.rebooked.book.BookChangedEvent;
import com.personal.rebooked.conversation.ChatEvents;
import com.personal.rebooked.exception.ApiException;
import com.personal.rebooked.notification.models.Notification;
import com.personal.rebooked.notification.repositories.NotificationRepository;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.bson.types.ObjectId;
import org.springframework.context.event.EventListener;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.FindAndModifyOptions;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.index.Index;
import org.springframework.data.mongodb.core.index.PartialIndexFilter;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.http.HttpStatus;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;

import java.util.*;

import static org.springframework.data.mongodb.core.query.Criteria.where;
import static org.springframework.data.mongodb.core.query.Query.query;

/** The in-app inbox: storing, grouping, reading and marking notifications read, plus the live events. */
@Slf4j
@Service
@RequiredArgsConstructor
public class InAppNotifications {
    private static final int DEFAULT_PAGE_SIZE = 30;
    private static final int MAX_PAGE_SIZE = 50;

    private final MongoTemplate mongoTemplate;
    private final NotificationRepository notificationRepository;
    private final SimpMessagingTemplate messagingTemplate;

    /** At most one unread notification per group key per user. */
    @PostConstruct
    void ensureIndexes() {
        mongoTemplate.indexOps(Notification.class).ensureIndex(new Index()
                .on("userId", Sort.Direction.ASC).on("openGroupKey", Sort.Direction.ASC)
                .unique().named("user_open_group_unique")
                .partial(PartialIndexFilter.of(where("openGroupKey").exists(true))));
    }

    /** Stores the notification (merging into an open group if it has a groupKey) and pushes it live. */
    public Notification create(NotificationRequest request) {
        Notification saved = request.groupKey() == null ? insert(request) : upsertGroup(request);
        long unread = unreadTotal(request.recipientId());
        messagingTemplate.convertAndSendToUser(request.recipientId(), ChatEvents.QUEUE,
                NotificationEvents.notification(saved, unread));
        return saved;
    }

    private Notification insert(NotificationRequest request) {
        Date now = new Date();
        Notification n = new Notification();
        n.setUserId(request.recipientId());
        n.setType(request.type());
        n.setActor(request.actor());
        n.setBook(request.book());
        n.setConversationId(request.conversationId());
        n.setPreview(request.preview());
        n.setCount(1);
        n.setCreatedAt(now);
        n.setUpdatedAt(now);
        return notificationRepository.insert(n);
    }

    private Notification upsertGroup(NotificationRequest request) {
        Query open = query(where("userId").is(request.recipientId()).and("openGroupKey").is(request.groupKey()));
        Date now = new Date();
        Update update = new Update()
                .set("type", request.type())
                .set("actor", request.actor())
                .set("book", request.book())
                .set("conversationId", request.conversationId())
                .set("preview", request.preview())
                .set("updatedAt", now)
                .inc("count", 1)
                .setOnInsert("groupKey", request.groupKey())
                .setOnInsert("createdAt", now);
        FindAndModifyOptions options = FindAndModifyOptions.options().upsert(true).returnNew(true);
        try {
            return mongoTemplate.findAndModify(open, update, options, Notification.class);
        } catch (DuplicateKeyException e) {
            // Two upserts raced to create the group; the other one won, so this one updates it
            return mongoTemplate.findAndModify(open, update, options, Notification.class);
        }
    }

    public long unreadTotal(String userId) {
        return notificationRepository.countByUserIdAndReadAtIsNull(userId);
    }

    public NotificationPage list(String userId, String filter, Integer page, Integer pageSize) {
        Map<String, String> errors = new LinkedHashMap<>();
        String f = filter == null || filter.isBlank() ? "ALL" : filter.trim().toUpperCase();
        if (!f.equals("ALL") && !f.equals("UNREAD")) errors.put("filter", "Filter must be ALL or UNREAD");
        int p = page == null ? 0 : page;
        int size = pageSize == null ? DEFAULT_PAGE_SIZE : pageSize;
        if (p < 0) errors.put("page", "Page can't be negative");
        if (size < 1 || size > MAX_PAGE_SIZE) errors.put("pageSize", "Page size must be between 1 and " + MAX_PAGE_SIZE);
        if (!errors.isEmpty()) throw new ApiException(HttpStatus.BAD_REQUEST, "Validation failed", errors);

        Criteria criteria = where("userId").is(userId);
        if (f.equals("UNREAD")) criteria = criteria.and("readAt").is(null);
        Query query = query(criteria);
        long total = mongoTemplate.count(query, Notification.class);
        query.with(Sort.by(Sort.Order.desc("updatedAt"), Sort.Order.desc("_id"))).skip((long) p * size).limit(size);
        List<Notification> content = mongoTemplate.find(query, Notification.class);
        return new NotificationPage(content, p, size, total, (int) Math.ceil((double) total / size), unreadTotal(userId));
    }

    /**
     * Marks the user's notifications read (ids that aren't theirs are ignored), then tells all of their sessions.
     * Returns the new unread total.
     */
    public long markRead(String userId, List<String> ids, boolean all) {
        Criteria criteria = where("userId").is(userId).and("readAt").is(null);
        List<String> marked;
        if (all) {
            marked = List.of();
        } else {
            List<ObjectId> objectIds = ids.stream().filter(ObjectId::isValid).map(ObjectId::new).toList();
            marked = mongoTemplate.find(query(criteria.and("_id").in(objectIds)), Notification.class)
                    .stream().map(Notification::getId).toList();
            if (marked.isEmpty()) {
                long unread = unreadTotal(userId);
                messagingTemplate.convertAndSendToUser(userId, ChatEvents.QUEUE, NotificationEvents.read(List.of(), false, unread));
                return unread;
            }
            criteria = where("userId").is(userId).and("_id").in(marked.stream().map(ObjectId::new).toList());
        }
        mongoTemplate.updateMulti(query(criteria),
                new Update().set("readAt", new Date()).unset("openGroupKey"), Notification.class);
        long unread = unreadTotal(userId);
        messagingTemplate.convertAndSendToUser(userId, ChatEvents.QUEUE, NotificationEvents.read(marked, all, unread));
        return unread;
    }

    /** Reading a conversation reads its open NEW_MESSAGES notification. */
    public void markConversationRead(String userId, String conversationId) {
        List<String> ids = mongoTemplate.find(query(where("userId").is(userId).and("conversationId").is(conversationId)
                        .and("type").is(NotificationType.NEW_MESSAGES).and("readAt").is(null)), Notification.class)
                .stream().map(Notification::getId).toList();
        if (!ids.isEmpty()) markRead(userId, ids, false);
    }

    /** Has the book had an enquiry since the given date? */
    public boolean hadEnquirySince(String bookId, Date since) {
        return mongoTemplate.exists(query(where("type").is(NotificationType.BOOK_ENQUIRY)
                .and("book.id").is(bookId).and("createdAt").gte(since)), Notification.class);
    }

    public long deleteReadBefore(Date cutoff) {
        return mongoTemplate.remove(query(where("readAt").lt(cutoff)), Notification.class).getDeletedCount();
    }

    /** Keep the book snapshot on unread notifications current when the seller edits the book. */
    @EventListener
    public void onBookChanged(BookChangedEvent event) {
        if (event.kind() != BookChangedEvent.Kind.UPDATED) return;
        try {
            BookSnapshot book = BookSnapshot.of(event.book());
            mongoTemplate.updateMulti(query(where("book.id").is(book.id()).and("readAt").is(null)),
                    new Update().set("book", book), Notification.class);
        } catch (Exception e) {
            log.warn("Couldn't refresh notification snapshots for book {}: {}", event.book().getId(), e.getMessage());
        }
    }
}

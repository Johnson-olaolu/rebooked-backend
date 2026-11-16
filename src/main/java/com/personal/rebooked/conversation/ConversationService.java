package com.personal.rebooked.conversation;

import com.personal.rebooked.book.BookChangedEvent;
import com.personal.rebooked.book.models.Book;
import com.personal.rebooked.book.repositories.BookRepository;
import com.personal.rebooked.conversation.dto.*;
import com.personal.rebooked.conversation.models.*;
import com.personal.rebooked.conversation.repositories.ConversationRepository;
import com.personal.rebooked.conversation.repositories.MessageRepository;
import com.personal.rebooked.exception.ApiException;
import com.personal.rebooked.notification.InAppNotifications;
import com.personal.rebooked.notification.NotificationService;
import com.personal.rebooked.notification.Notifications;
import com.personal.rebooked.utils.Constants;
import com.personal.rebooked.utils.Constants.ConversationBookState;
import com.personal.rebooked.utils.Constants.SystemEvent;
import com.mongodb.client.result.UpdateResult;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.bson.Document;
import org.bson.types.ObjectId;
import org.springframework.context.event.EventListener;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.aggregation.Aggregation;
import org.springframework.data.mongodb.core.index.Index;
import org.springframework.data.mongodb.core.index.PartialIndexFilter;
import org.springframework.data.mongodb.core.query.BasicQuery;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.regex.Pattern;

import static org.springframework.data.mongodb.core.query.Criteria.where;
import static org.springframework.data.mongodb.core.query.Query.query;

/**
 * Chat: one conversation per pair of users, with each book they talk about attached to it.
 * Commands come over STOMP (ConversationGateway), paged reads over REST (ConversationController).
 * A conversation the caller isn't in and an unknown book are both "not found".
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ConversationService {
    private static final int DEFAULT_PAGE_SIZE = 20;
    private static final int MAX_PAGE_SIZE = 50;
    private static final int DEFAULT_MESSAGE_LIMIT = 30;
    private static final int MAX_MESSAGE_LIMIT = 50;
    private static final List<String> FILTERS = List.of("ALL", "UNREAD", "SELLING", "BUYING");
    /** Entry states whose buyer the seller can mark the book as sold to. */
    private static final Set<ConversationBookState> SELLABLE_STATES =
            EnumSet.of(ConversationBookState.OPEN, ConversationBookState.UNAVAILABLE);

    private final ConversationRepository conversationRepository;
    private final MessageRepository messageRepository;
    private final BookRepository bookRepository;
    private final MongoTemplate mongoTemplate;
    private final ConversationViews views;
    private final ChatPublisher publisher;
    private final NotificationService notificationService;
    private final Notifications notifications;
    private final InAppNotifications inAppNotifications;
    private final com.personal.rebooked.user.repositories.UserRepository userRepository;

    /** Retries are matched on { senderId, clientId }; only TEXT messages carry a clientId. */
    @PostConstruct
    void ensureIndexes() {
        mongoTemplate.indexOps(Message.class).ensureIndex(new Index()
                .on("senderId", Sort.Direction.ASC).on("clientId", Sort.Direction.ASC)
                .unique().named("sender_client_unique")
                .partial(PartialIndexFilter.of(where("clientId").exists(true))));
    }

    // ---------------------------------------------------------------- commands (STOMP)

    public ConversationView start(String me, StartConversationPayload payload) {
        requireClientId(payload.clientId());
        if (isBlank(payload.bookId())) throw ChatException.invalid("bookId", "Book is required");
        String body = optionalBody(payload.body());

        if (body != null) {
            Optional<Message> retry = messageRepository.findBySenderIdAndClientId(me, payload.clientId());
            if (retry.isPresent()) return views.of(participantConversation(retry.get().getConversationId(), me), me);
        }

        Book book = visibleBook(payload.bookId());
        String sellerId = book.getUser() != null ? book.getUser().getId() : null;
        if (sellerId == null) throw ChatException.notFound();
        if (sellerId.equals(me)) {
            throw new ChatException(ChatException.Code.OWN_BOOK, "You can't start a conversation about your own book");
        }

        Optional<Conversation> existing = conversationRepository.findByPairKey(Conversation.pairKey(me, sellerId));
        boolean attached = existing.flatMap(c -> c.book(book.getId())).isPresent();
        if (!attached && book.getStatus() != Constants.BookStatus.ACTIVE) {
            throw new ChatException(ChatException.Code.BOOK_NOT_AVAILABLE, "This book is no longer available");
        }
        Conversation conversation = existing.orElseGet(() -> create(me, sellerId));

        boolean newlyAttached = !attached && attach(conversation, book, sellerId, me);
        if (newlyAttached) {
            conversation = reload(conversation);
            conversation = record(conversation, systemMessage(conversation.getId(), me, book.getId(), SystemEvent.BOOK_ADDED));
        }
        if (body != null) {
            Message text = textMessage(conversation.getId(), me, book.getId(), body, payload.clientId());
            if (text != null) conversation = record(conversation, text);
        }
        if (newlyAttached) {
            notificationService.send(notifications.bookEnquiry(sellerId, me,
                    Notifications.snapshot(conversation.book(book.getId()).orElse(null)), conversation.getId(), body));
        }
        publisher.conversation(conversation);
        return views.of(conversation, me);
    }

    public Message send(String me, SendMessagePayload payload) {
        requireClientId(payload.clientId());
        String body = requiredBody(payload.body());
        Optional<Message> retry = messageRepository.findBySenderIdAndClientId(me, payload.clientId());
        if (retry.isPresent()) return retry.get();

        Conversation conversation = participantConversation(payload.conversationId(), me);
        String bookId = isBlank(payload.bookId()) ? null : payload.bookId();
        if (bookId != null && conversation.book(bookId).isEmpty()) {
            throw ChatException.invalid("bookId", "That book isn't part of this conversation");
        }
        Message text = textMessage(conversation.getId(), me, bookId, body, payload.clientId());
        if (text == null) {
            // Lost a race with a retry of the same command
            return messageRepository.findBySenderIdAndClientId(me, payload.clientId()).orElseThrow();
        }
        record(conversation, text);
        return text;
    }

    public Map<String, Object> read(String me, ReadPayload payload) {
        requireClientId(payload.clientId());
        Conversation conversation = participantConversation(payload.conversationId(), me);
        if (isBlank(payload.upToMessageId())) throw ChatException.invalid("upToMessageId", "Message is required");
        String upTo = payload.upToMessageId();
        if (!ObjectId.isValid(upTo)) throw ChatException.notFound();
        Message target = messageRepository.findById(upTo)
                .filter(m -> conversation.getId().equals(m.getConversationId()))
                .orElseThrow(ChatException::notFound);

        String stored = conversation.readStateOf(me).getLastReadMessageId();
        if (stored == null || !ObjectId.isValid(stored) || new ObjectId(upTo).compareTo(new ObjectId(stored)) > 0) {
            // Anything the other user sent after this message is still unread
            long remaining = mongoTemplate.count(query(where("conversationId").is(conversation.getId())
                    .and("_id").gt(new ObjectId(upTo)).and("senderId").ne(me)
                    .and("type").is(Constants.MessageType.TEXT)), Message.class);
            ReadState state = new ReadState();
            state.setLastReadMessageId(target.getId());
            state.setLastReadAt(new Date());
            state.setUnread((int) remaining);
            mongoTemplate.updateFirst(query(where("_id").is(conversation.getId())),
                    new Update().set("readState." + me, state), Conversation.class);
            publisher.send(conversation.otherParticipant(me),
                    ChatEvents.read(conversation.getId(), me, target.getId(), state.getLastReadAt()));
        }
        inAppNotifications.markConversationRead(me, conversation.getId());
        return Map.of("unreadTotal", unreadTotal(me));
    }

    public ConversationView bookSold(String me, BookSoldPayload payload) {
        requireClientId(payload.clientId());
        Conversation conversation = participantConversation(payload.conversationId(), me);
        if (isBlank(payload.bookId())) throw ChatException.invalid("bookId", "Book is required");
        String bookId = payload.bookId();
        if (conversation.book(bookId).isEmpty()) throw ChatException.notFound();
        Book book = bookRepository.findById(bookId).orElseThrow(ChatException::notFound);
        if (book.getUser() == null || !me.equals(book.getUser().getId())) {
            throw new ChatException(ChatException.Code.FORBIDDEN, "Only the seller can mark this book as sold");
        }
        String buyerId = conversation.otherParticipant(me);
        if (book.getStatus() == Constants.BookStatus.SOLD && buyerId.equals(book.getBuyerId())) {
            return views.of(conversation, me); // a retry
        }
        Conversation updated = markSoldTo(book, conversation, me);
        if (updated == null) {
            throw new ChatException(ChatException.Code.BOOK_NOT_AVAILABLE, "This book is no longer available");
        }
        return views.of(updated, me);
    }

    /**
     * Marks the book as sold to the other participant of {@code conversation}, from the chat or from
     * My shelf. That conversation goes to SOLD_TO_BUYER (SOLD_TO_YOU and RATE_SELLER for the buyer);
     * every other open entry for the book goes to SOLD_ELSEWHERE. Returns null if the book can no
     * longer be sold (it isn't ACTIVE or INACTIVE).
     */
    public Conversation markSoldTo(Book book, Conversation conversation, String sellerId) {
        String bookId = book.getId();
        String buyerId = conversation.otherParticipant(sellerId);
        List<Constants.BookStatus> sellable = List.of(Constants.BookStatus.ACTIVE, Constants.BookStatus.INACTIVE);
        UpdateResult sold = mongoTemplate.updateFirst(
                query(where("_id").is(bookId).and("status").in(sellable)),
                new Update().set("status", Constants.BookStatus.SOLD).set("soldDate", new Date())
                        .set("buyerId", buyerId).set("updatedAt", new Date()),
                Book.class);
        if (sold.getModifiedCount() == 0) return null;

        conversation = changeState(conversation, bookId, ConversationBookState.SOLD_TO_BUYER, SystemEvent.SOLD_TO_BUYER, sellerId);
        var snapshot = Notifications.snapshot(conversation.book(bookId).orElse(null));
        notificationService.send(notifications.soldToYou(buyerId, sellerId, snapshot, conversation.getId()));
        for (Conversation other : conversationRepository.findByBooksBookId(bookId)) {
            if (other.getId().equals(conversation.getId())) continue;
            ConversationBook entry = other.book(bookId).orElse(null);
            ConversationBookState state = entry != null ? entry.getState() : null;
            if (state == ConversationBookState.OPEN || state == ConversationBookState.UNAVAILABLE) {
                changeState(other, bookId, ConversationBookState.SOLD_ELSEWHERE, SystemEvent.SOLD_ELSEWHERE, sellerId);
                notificationService.send(notifications.soldElsewhere(entry.getBuyerId(), sellerId,
                        Notifications.snapshot(entry), other.getId()));
            }
        }
        // With the book's slug, so the email can link to it
        notificationService.send(notifications.rateSeller(buyerId, sellerId,
                Notifications.snapshot(bookRepository.findById(bookId).orElse(book)), conversation.getId()));
        return conversation;
    }

    /**
     * GET /book/{id}/chat-buyers: who the seller can mark the book as sold to. The buyers with this
     * book OPEN or UNAVAILABLE in a conversation, most recent first.
     */
    public List<ChatBuyer> chatBuyers(String bookId) {
        List<ChatBuyer> buyers = new ArrayList<>();
        for (Conversation conversation : conversationRepository.findByBooksBookId(bookId)) {
            ConversationBook entry = conversation.book(bookId).orElse(null);
            if (entry == null || !SELLABLE_STATES.contains(entry.getState()) || entry.getBuyerId() == null) continue;
            Date last = entry.getLastMessageAt() != null ? entry.getLastMessageAt() : entry.getAddedAt();
            buyers.add(new ChatBuyer(entry.getBuyerId(), null, null, conversation.getId(), last));
        }
        Map<String, com.personal.rebooked.user.models.User> users = new HashMap<>();
        userRepository.findAllById(buyers.stream().map(ChatBuyer::userId).filter(ObjectId::isValid).toList())
                .forEach(u -> users.put(u.getId(), u));
        return buyers.stream()
                .filter(b -> users.containsKey(b.userId()))
                .map(b -> {
                    var profile = users.get(b.userId()).getProfile();
                    return new ChatBuyer(b.userId(), profile != null ? profile.getUserName() : null,
                            profile != null ? profile.getProfilePictureUrl() : null, b.conversationId(), b.lastMessageAt());
                })
                .sorted(Comparator.comparing(ChatBuyer::lastMessageAt, Comparator.nullsLast(Comparator.reverseOrder())))
                .toList();
    }

    /** The conversation in which {@code buyerId} can be named as the buyer of this book, if any. */
    public Optional<Conversation> sellableConversation(String bookId, String sellerId, String buyerId) {
        return conversationRepository.findByPairKey(Conversation.pairKey(sellerId, buyerId))
                .filter(c -> c.book(bookId).map(e -> SELLABLE_STATES.contains(e.getState())
                        && buyerId.equals(e.getBuyerId())).orElse(false));
    }

    // ---------------------------------------------------------------- book changes from My shelf (18d)

    @EventListener
    public void onBookChanged(BookChangedEvent event) {
        Book book = event.book();
        String actorId = book.getUser() != null ? book.getUser().getId() : null;
        try {
            switch (event.kind()) {
                case UPDATED -> refreshSnapshot(book);
                case DELETED -> transition(book.getId(), actorId, Set.of(ConversationBookState.OPEN),
                        ConversationBookState.UNAVAILABLE, SystemEvent.BOOK_UNAVAILABLE);
                case STATUS_CHANGED -> {
                    switch (book.getStatus()) {
                        case SOLD -> transition(book.getId(), actorId,
                                Set.of(ConversationBookState.OPEN, ConversationBookState.UNAVAILABLE),
                                ConversationBookState.SOLD_ELSEWHERE, SystemEvent.SOLD_ELSEWHERE);
                        case INACTIVE -> transition(book.getId(), actorId, Set.of(ConversationBookState.OPEN),
                                ConversationBookState.UNAVAILABLE, SystemEvent.BOOK_UNAVAILABLE);
                        case ACTIVE -> transition(book.getId(), actorId,
                                Set.of(ConversationBookState.SOLD_TO_BUYER, ConversationBookState.SOLD_ELSEWHERE,
                                        ConversationBookState.UNAVAILABLE),
                                ConversationBookState.OPEN, SystemEvent.BOOK_AVAILABLE);
                        default -> { }
                    }
                }
            }
        } catch (Exception e) {
            // The book change itself has succeeded; don't fail the request over chat bookkeeping
            log.error("Couldn't update conversations for book {}", book.getId(), e);
        }
    }

    private void transition(String bookId, String actorId, Set<ConversationBookState> from,
                            ConversationBookState to, SystemEvent event) {
        for (Conversation conversation : conversationRepository.findByBooksBookId(bookId)) {
            ConversationBook entry = conversation.book(bookId).orElse(null);
            if (entry == null || entry.getState() == null || !from.contains(entry.getState())) continue;
            changeState(conversation, bookId, to, event, actorId);
            // A buyer whose own purchase fell through isn't told "available again"
            if (!(event == SystemEvent.BOOK_AVAILABLE && entry.getState() == ConversationBookState.SOLD_TO_BUYER)) {
                notifyBuyer(entry, conversation.getId(), actorId, event);
            }
        }
    }

    /** The buyer of this conversation book hears about shelf changes; the seller caused them, so they don't. */
    private void notifyBuyer(ConversationBook entry, String conversationId, String sellerId, SystemEvent event) {
        var snapshot = Notifications.snapshot(entry);
        var request = switch (event) {
            case SOLD_ELSEWHERE -> notifications.soldElsewhere(entry.getBuyerId(), sellerId, snapshot, conversationId);
            case BOOK_UNAVAILABLE -> notifications.bookUnavailable(entry.getBuyerId(), sellerId, snapshot, conversationId);
            case BOOK_AVAILABLE -> notifications.bookAvailable(entry.getBuyerId(), sellerId, snapshot, conversationId);
            default -> null;
        };
        if (request != null) notificationService.send(request);
    }

    private void refreshSnapshot(Book book) {
        mongoTemplate.updateMulti(query(where("books.bookId").is(book.getId())),
                new Update().set("books.$[b].title", book.getTitle()).set("books.$[b].coverUrl", coverUrl(book))
                        .filterArray(where("b.bookId").is(book.getId())),
                Conversation.class);
    }

    private Conversation changeState(Conversation conversation, String bookId, ConversationBookState state,
                                     SystemEvent event, String actorId) {
        mongoTemplate.updateFirst(query(where("_id").is(conversation.getId())),
                new Update().set("books.$[b].state", state).set("updatedAt", new Date())
                        .filterArray(where("b.bookId").is(bookId)),
                Conversation.class);
        Conversation updated = record(reload(conversation), systemMessage(conversation.getId(), actorId, bookId, event));
        publisher.conversation(updated);
        return updated;
    }

    // ---------------------------------------------------------------- reads (REST)

    public ConversationPage list(String me, String filter, String search, Integer page, Integer pageSize) {
        Map<String, String> errors = new LinkedHashMap<>();
        String f = isBlank(filter) ? "ALL" : filter.trim().toUpperCase();
        if (!FILTERS.contains(f)) errors.put("filter", "Filter must be one of " + String.join(", ", FILTERS));
        int p = page == null ? 0 : page;
        int size = pageSize == null ? DEFAULT_PAGE_SIZE : pageSize;
        if (p < 0) errors.put("page", "Page can't be negative");
        if (size < 1 || size > MAX_PAGE_SIZE) errors.put("pageSize", "Page size must be between 1 and " + MAX_PAGE_SIZE);
        if (!errors.isEmpty()) throw new ApiException(HttpStatus.BAD_REQUEST, "Validation failed", errors);

        List<Criteria> criteria = new ArrayList<>(List.of(mine(me)));
        Criteria filterCriteria = filterCriteria(me, f);
        if (filterCriteria != null) criteria.add(filterCriteria);
        if (!isBlank(search)) criteria.add(searchCriteria(me, search.trim()));

        Query query = new Query(new Criteria().andOperator(criteria));
        long total = mongoTemplate.count(query, Conversation.class);
        query.with(Sort.by(Sort.Order.desc("lastMessageAt"), Sort.Order.desc("_id"))).skip((long) p * size).limit(size);
        List<Conversation> conversations = mongoTemplate.find(query, Conversation.class);

        Map<String, Long> counts = new LinkedHashMap<>();
        for (String name : FILTERS) {
            Criteria c = filterCriteria(me, name);
            counts.put(name, mongoTemplate.count(new Query(c == null ? mine(me) : new Criteria().andOperator(mine(me), c)),
                    Conversation.class));
        }
        return new ConversationPage(views.of(conversations, me), p, size, total,
                (int) Math.ceil((double) total / size), counts);
    }

    public ConversationView get(String me, String id) {
        return views.of(participantConversationOr404(id, me), me);
    }

    public MessagePage messages(String me, String id, String before, Integer limit) {
        Conversation conversation = participantConversationOr404(id, me);
        int size = limit == null ? DEFAULT_MESSAGE_LIMIT : limit;
        if (size < 1 || size > MAX_MESSAGE_LIMIT) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Validation failed",
                    Map.of("limit", "Limit must be between 1 and " + MAX_MESSAGE_LIMIT));
        }
        Criteria criteria = where("conversationId").is(conversation.getId());
        if (!isBlank(before)) {
            if (!ObjectId.isValid(before)) {
                throw new ApiException(HttpStatus.BAD_REQUEST, "Validation failed", Map.of("before", "Invalid message id"));
            }
            criteria = criteria.and("_id").lt(new ObjectId(before));
        }
        Query query = query(criteria).with(Sort.by(Sort.Direction.DESC, "_id")).limit(size + 1);
        List<Message> found = mongoTemplate.find(query, Message.class);
        boolean hasMore = found.size() > size;
        return new MessagePage(hasMore ? found.subList(0, size) : found, hasMore);
    }

    /** The caller's conversation with a book's seller or with a user, or null. Never a 404. */
    public ConversationWith with(String me, String bookId, String userId) {
        if (isBlank(bookId) == isBlank(userId)) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Validation failed",
                    Map.of("bookId", "Send either bookId or userId"));
        }
        String otherId = userId;
        if (!isBlank(bookId)) {
            if (!ObjectId.isValid(bookId)) return null;
            Book book = bookRepository.findById(bookId).orElse(null);
            if (book == null || book.getUser() == null) return null;
            otherId = book.getUser().getId();
        }
        if (otherId == null || otherId.equals(me)) return null;
        Conversation conversation = conversationRepository.findByPairKey(Conversation.pairKey(me, otherId)).orElse(null);
        if (conversation == null) return null;
        return new ConversationWith(conversation.getId(), !isBlank(bookId) && conversation.book(bookId).isPresent());
    }

    public long unreadTotal(String me) {
        String field = "readState." + me + ".unread";
        Aggregation aggregation = Aggregation.newAggregation(
                Aggregation.match(where("participantIds").is(me).and(field).gt(0)),
                Aggregation.group().sum(field).as("total"));
        Document result = mongoTemplate.aggregate(aggregation, "conversations", Document.class).getUniqueMappedResult();
        return result != null && result.get("total") instanceof Number n ? n.longValue() : 0;
    }

    // ---------------------------------------------------------------- helpers

    private Conversation create(String a, String b) {
        Conversation conversation = new Conversation();
        conversation.setParticipantIds(a.compareTo(b) < 0 ? List.of(a, b) : List.of(b, a));
        conversation.setPairKey(Conversation.pairKey(a, b));
        conversation.getReadState().put(a, new ReadState());
        conversation.getReadState().put(b, new ReadState());
        try {
            return conversationRepository.insert(conversation);
        } catch (DuplicateKeyException e) {
            return conversationRepository.findByPairKey(conversation.getPairKey()).orElseThrow();
        }
    }

    /** Attaches the book unless it's already there; true when this call attached it. */
    private boolean attach(Conversation conversation, Book book, String sellerId, String buyerId) {
        ConversationBook entry = new ConversationBook();
        entry.setBookId(book.getId());
        entry.setSellerId(sellerId);
        entry.setBuyerId(buyerId);
        entry.setTitle(book.getTitle());
        entry.setCoverUrl(coverUrl(book));
        entry.setAddedAt(new Date());
        entry.setAddedBy(buyerId);
        entry.setState(ConversationBookState.OPEN);
        UpdateResult result = mongoTemplate.updateFirst(
                query(where("_id").is(conversation.getId()).and("books.bookId").ne(book.getId())),
                new Update().push("books", entry).set("updatedAt", new Date()), Conversation.class);
        return result.getModifiedCount() > 0;
    }

    /** Saves a TEXT message; null if this { sender, clientId } already exists. */
    private Message textMessage(String conversationId, String senderId, String bookId, String body, String clientId) {
        Message message = new Message();
        message.setConversationId(conversationId);
        message.setSenderId(senderId);
        message.setBookId(bookId);
        message.setType(Constants.MessageType.TEXT);
        message.setBody(body);
        message.setClientId(clientId);
        try {
            return messageRepository.insert(message);
        } catch (DuplicateKeyException e) {
            return null;
        }
    }

    private Message systemMessage(String conversationId, String actorId, String bookId, SystemEvent event) {
        Message message = new Message();
        message.setConversationId(conversationId);
        message.setSenderId(actorId);
        message.setBookId(bookId);
        message.setType(Constants.MessageType.SYSTEM);
        message.setSystemEvent(event);
        return messageRepository.insert(message);
    }

    /** Updates lastMessage, the book's lastMessageAt and (TEXT only) the other user's unread count, then pushes "message". */
    private Conversation record(Conversation conversation, Message message) {
        Date at = message.getCreatedAt() != null ? message.getCreatedAt() : new Date();
        LastMessage last = new LastMessage(message.getId(), preview(conversation, message), message.getSenderId(),
                message.getBookId(), message.getType(), at);
        Update update = new Update().set("lastMessage", last).set("lastMessageAt", at).set("updatedAt", new Date());
        // Only TEXT lights up the badge; system lines still show in the thread
        if (message.getType() == Constants.MessageType.TEXT) {
            update.inc("readState." + conversation.otherParticipant(message.getSenderId()) + ".unread", 1);
        }
        if (message.getBookId() != null && conversation.book(message.getBookId()).isPresent()) {
            update.set("books.$[b].lastMessageAt", at).filterArray(where("b.bookId").is(message.getBookId()));
        }
        mongoTemplate.updateFirst(query(where("_id").is(conversation.getId())), update, Conversation.class);
        Conversation updated = reload(conversation);
        publisher.message(updated, message);
        return updated;
    }

    private static String preview(Conversation conversation, Message message) {
        String text;
        if (message.getType() == Constants.MessageType.TEXT) {
            text = message.getBody();
        } else {
            String title = conversation.book(message.getBookId()).map(ConversationBook::getTitle).orElse("This book");
            text = switch (message.getSystemEvent()) {
                case BOOK_ADDED -> "Asked about " + title;
                case SOLD_TO_BUYER -> title + " was sold in this chat";
                case SOLD_ELSEWHERE -> title + " was sold to someone else";
                case BOOK_UNAVAILABLE -> title + " is no longer available";
                case BOOK_AVAILABLE -> title + " is available again";
            };
        }
        return text.length() > Constants.MESSAGE_PREVIEW_LENGTH
                ? text.substring(0, Constants.MESSAGE_PREVIEW_LENGTH - 1) + "…" : text;
    }

    private Conversation reload(Conversation conversation) {
        return conversationRepository.findById(conversation.getId()).orElseThrow(ChatException::notFound);
    }

    private Conversation participantConversation(String id, String me) {
        if (isBlank(id)) throw ChatException.invalid("conversationId", "Conversation is required");
        if (!ObjectId.isValid(id)) throw ChatException.notFound();
        return conversationRepository.findById(id)
                .filter(c -> c.getParticipantIds().contains(me))
                .orElseThrow(ChatException::notFound);
    }

    private Conversation participantConversationOr404(String id, String me) {
        try {
            return participantConversation(id, me);
        } catch (ChatException e) {
            throw new ApiException(HttpStatus.NOT_FOUND, "Conversation not found");
        }
    }

    /** A book that exists and isn't a draft. */
    private Book visibleBook(String id) {
        if (!ObjectId.isValid(id)) throw ChatException.notFound();
        return bookRepository.findById(id)
                .filter(b -> b.getStatus() != Constants.BookStatus.DRAFT)
                .orElseThrow(ChatException::notFound);
    }

    private static Criteria mine(String me) {
        return where("participantIds").is(me);
    }

    private static Criteria filterCriteria(String me, String filter) {
        return switch (filter) {
            case "UNREAD" -> where("readState." + me + ".unread").gt(0);
            case "SELLING" -> where("books.sellerId").is(me);
            case "BUYING" -> where("books.buyerId").is(me);
            default -> null;
        };
    }

    /** The other user's userName or any attached book's title, case-insensitive. */
    private Criteria searchCriteria(String me, String search) {
        Pattern pattern = Pattern.compile(Pattern.quote(search), Pattern.CASE_INSENSITIVE);
        Query profiles = query(where("userName").regex(pattern));
        profiles.fields().include("_id");
        List<ObjectId> profileIds = mongoTemplate.find(profiles, Document.class, "profile").stream()
                .map(d -> d.getObjectId("_id")).toList();
        List<String> userIds = new ArrayList<>();
        if (!profileIds.isEmpty()) {
            BasicQuery users = new BasicQuery(new Document("profile.$id", new Document("$in", profileIds)),
                    new Document("_id", 1));
            mongoTemplate.find(users, Document.class, "user").forEach(d -> {
                String id = d.getObjectId("_id").toHexString();
                if (!id.equals(me)) userIds.add(id);
            });
        }
        return new Criteria().orOperator(where("participantIds").in(userIds), where("books.title").regex(pattern));
    }

    private static String coverUrl(Book book) {
        return book.getCoverImage() != null ? book.getCoverImage().getUrl() : null;
    }

    private static void requireClientId(String clientId) {
        if (isBlank(clientId)) throw ChatException.invalid("clientId", "clientId is required");
    }

    private static String requiredBody(String body) {
        String trimmed = body == null ? "" : body.trim();
        if (trimmed.isEmpty()) throw ChatException.invalid("body", "Message can't be empty");
        if (trimmed.length() > Constants.MESSAGE_MAX_LENGTH) {
            throw ChatException.invalid("body", "Messages can be at most " + Constants.MESSAGE_MAX_LENGTH + " characters");
        }
        return trimmed;
    }

    private static String optionalBody(String body) {
        return body == null || body.isBlank() ? null : requiredBody(body);
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}

package com.personal.rebooked.conversation;

import com.personal.rebooked.book.models.Book;
import com.personal.rebooked.conversation.dto.ConversationBookView;
import com.personal.rebooked.conversation.dto.ConversationView;
import com.personal.rebooked.conversation.models.Conversation;
import com.personal.rebooked.conversation.models.ConversationBook;
import com.personal.rebooked.user.dto.PublicUserDTO;
import com.personal.rebooked.user.models.User;
import com.personal.rebooked.user.repositories.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

/** Builds conversations as one participant sees them. The other user is only ever a PublicUserDTO. */
@Component
@RequiredArgsConstructor
public class ConversationViews {
    private static final Comparator<ConversationBookView> BY_LAST_MESSAGE = Comparator.comparing(
            (ConversationBookView b) -> b.lastMessageAt() != null ? b.lastMessageAt() : b.addedAt(),
            Comparator.nullsLast(Comparator.reverseOrder()));

    private final UserRepository userRepository;
    private final MongoTemplate mongoTemplate;

    public ConversationView of(Conversation conversation, String viewerId) {
        return of(List.of(conversation), viewerId).get(0);
    }

    public List<ConversationView> of(List<Conversation> conversations, String viewerId) {
        Set<String> userIds = conversations.stream().map(c -> c.otherParticipant(viewerId)).collect(Collectors.toSet());
        Set<String> bookIds = conversations.stream().flatMap(c -> c.getBooks().stream())
                .map(ConversationBook::getBookId).collect(Collectors.toSet());

        Map<String, User> users = new HashMap<>();
        userRepository.findAllById(userIds).forEach(u -> users.put(u.getId(), u));
        Map<String, Book> books = liveBooks(bookIds);

        return conversations.stream().map(c -> view(c, viewerId, users, books)).toList();
    }

    private ConversationView view(Conversation c, String viewerId, Map<String, User> users, Map<String, Book> books) {
        String otherId = c.otherParticipant(viewerId);
        List<ConversationBookView> bookViews = c.getBooks().stream().map(cb -> {
            Book live = books.get(cb.getBookId());
            return new ConversationBookView(cb.getBookId(), cb.getTitle(), cb.getCoverUrl(),
                    live != null ? live.getPrice() : null,
                    live != null ? live.isFree() : null,
                    live != null ? live.getCondition() : null,
                    live != null ? live.getStatus() : null,
                    cb.getState(),
                    viewerId.equals(cb.getSellerId()) ? "SELLER" : "BUYER",
                    cb.getAddedAt(), cb.getLastMessageAt());
        }).sorted(BY_LAST_MESSAGE).toList();

        User other = users.get(otherId);
        return new ConversationView(c.getId(),
                other != null ? PublicUserDTO.from(other) : deletedUser(otherId),
                bookViews, c.getLastMessage(), c.readStateOf(viewerId).getUnread(),
                c.readStateOf(otherId).getLastReadMessageId(), c.getCreatedAt(), c.getUpdatedAt());
    }

    /** Only the fields the rows need, so the book's DBRefs aren't loaded. */
    private Map<String, Book> liveBooks(Set<String> ids) {
        if (ids.isEmpty()) return Map.of();
        Query query = new Query(Criteria.where("_id").in(ids));
        query.fields().include("price", "isFree", "condition", "status");
        return mongoTemplate.find(query, Book.class).stream().collect(Collectors.toMap(Book::getId, Function.identity()));
    }

    private static PublicUserDTO deletedUser(String id) {
        return PublicUserDTO.deleted(id);
    }
}

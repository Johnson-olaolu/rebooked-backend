package com.personal.rebooked.notification;

import com.personal.rebooked.book.models.Book;
import com.personal.rebooked.conversation.models.ConversationBook;
import com.personal.rebooked.user.models.User;
import com.personal.rebooked.user.repositories.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.Map;

/** One factory per notification, so senders never assemble requests by hand. Pass the result to NotificationService.send. */
@Component
@RequiredArgsConstructor
public class Notifications {
    private final UserRepository userRepository;

    // ---------------------------------------------------------------- account

    public NotificationRequest emailVerification(User user) {
        return base(NotificationType.EMAIL_VERIFICATION, user.getId())
                .data(Map.of("code", user.getConfirmEmailToken())).build();
    }

    public NotificationRequest passwordReset(User user, String resetUrl) {
        return base(NotificationType.PASSWORD_RESET, user.getId()).data(Map.of("url", resetUrl)).build();
    }

    public NotificationRequest passwordChanged(User user) {
        return base(NotificationType.PASSWORD_CHANGED, user.getId()).build();
    }

    public NotificationRequest welcome(User user) {
        return base(NotificationType.WELCOME, user.getId()).dedupeKey("WELCOME:" + user.getId()).build();
    }

    public NotificationRequest onboardingReminder(String userId) {
        return base(NotificationType.ONBOARDING_REMINDER, userId).dedupeKey("ONBOARD:" + userId).build();
    }

    // ---------------------------------------------------------------- chat

    public NotificationRequest bookEnquiry(String sellerId, String buyerId, BookSnapshot book, String conversationId,
                                           String firstMessage) {
        return base(NotificationType.BOOK_ENQUIRY, sellerId).actor(actor(buyerId)).book(book)
                .conversationId(conversationId).preview(firstMessage).build();
    }

    public NotificationRequest newMessages(String recipientId, String senderId, BookSnapshot book, String conversationId,
                                           String preview) {
        return base(NotificationType.NEW_MESSAGES, recipientId).actor(actor(senderId)).book(book)
                .conversationId(conversationId).preview(preview)
                .groupKey("NEW_MESSAGES:" + conversationId).build();
    }

    public NotificationRequest unrepliedMessage(String recipientId, String senderId, BookSnapshot book,
                                                String conversationId, String preview, String messageId) {
        return base(NotificationType.UNREPLIED_MESSAGE, recipientId).actor(actor(senderId)).book(book)
                .conversationId(conversationId).preview(preview).dedupeKey("UNREPLIED:" + messageId).build();
    }

    // ---------------------------------------------------------------- sales

    public NotificationRequest soldToYou(String buyerId, String sellerId, BookSnapshot book, String conversationId) {
        return sale(NotificationType.SOLD_TO_YOU, buyerId, sellerId, book, conversationId);
    }

    public NotificationRequest soldElsewhere(String buyerId, String sellerId, BookSnapshot book, String conversationId) {
        return sale(NotificationType.SOLD_ELSEWHERE, buyerId, sellerId, book, conversationId);
    }

    public NotificationRequest bookUnavailable(String buyerId, String sellerId, BookSnapshot book, String conversationId) {
        return sale(NotificationType.BOOK_UNAVAILABLE, buyerId, sellerId, book, conversationId);
    }

    public NotificationRequest bookAvailable(String buyerId, String sellerId, BookSnapshot book, String conversationId) {
        return sale(NotificationType.BOOK_AVAILABLE, buyerId, sellerId, book, conversationId);
    }

    public NotificationRequest markAsSoldNudge(String sellerId, BookSnapshot book, String month) {
        return base(NotificationType.MARK_AS_SOLD_NUDGE, sellerId).book(book)
                .dedupeKey("NUDGE:" + book.id() + ":" + month).build();
    }

    // ---------------------------------------------------------------- listings

    public NotificationRequest staleListing(String sellerId, BookSnapshot book, String month) {
        return base(NotificationType.STALE_LISTING, sellerId).book(book)
                .dedupeKey("STALE:" + book.id() + ":" + month).build();
    }

    public NotificationRequest draftReminder(String sellerId, BookSnapshot book) {
        return base(NotificationType.DRAFT_REMINDER, sellerId).book(book).dedupeKey("DRAFT:" + book.id()).build();
    }

    public NotificationRequest viewsMilestone(String sellerId, BookSnapshot book, int views) {
        return base(NotificationType.VIEWS_MILESTONE, sellerId).book(book).preview(views + " views")
                .data(Map.of("views", views)).dedupeKey("VIEWS:" + book.id() + ":" + views).build();
    }

    // ---------------------------------------------------------------- helpers

    public static BookSnapshot snapshot(ConversationBook book) {
        return book == null ? null : new BookSnapshot(book.getBookId(), book.getTitle(), book.getCoverUrl(), null);
    }

    public static BookSnapshot snapshot(Book book) {
        return BookSnapshot.of(book);
    }

    private NotificationRequest sale(NotificationType type, String buyerId, String sellerId, BookSnapshot book,
                                     String conversationId) {
        return base(type, buyerId).actor(actor(sellerId)).book(book).conversationId(conversationId).build();
    }

    private ActorSnapshot actor(String userId) {
        if (userId == null) return null;
        return userRepository.findById(userId).map(ActorSnapshot::of)
                .orElse(new ActorSnapshot(userId, null, null));
    }

    private static NotificationRequest.NotificationRequestBuilder base(NotificationType type, String recipientId) {
        return NotificationRequest.builder().type(type).recipientId(recipientId);
    }
}

package com.personal.rebooked.notification;

import com.personal.rebooked.book.models.Book;
import com.personal.rebooked.conversation.models.Conversation;
import com.personal.rebooked.conversation.models.ConversationBook;
import com.personal.rebooked.user.models.Profile;
import com.personal.rebooked.user.models.User;
import com.personal.rebooked.utils.Constants;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.time.YearMonth;
import java.time.ZoneId;
import java.util.*;

import static org.springframework.data.mongodb.core.query.Criteria.where;
import static org.springframework.data.mongodb.core.query.Query.query;

/**
 * Smart and scheduled notifications. Each one carries a dedupeKey, so a job can run any number of times
 * without sending twice. Timings: see docs/notification.md.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class NotificationJobs {
    static final Duration UNREPLIED_AFTER = Duration.ofMinutes(30);
    static final Duration UNREPLIED_WINDOW = Duration.ofHours(24);
    static final Duration STALE_AFTER = Duration.ofDays(30);
    static final Duration DRAFT_AFTER = Duration.ofDays(3);
    static final Duration QUIET_CHAT_AFTER = Duration.ofDays(7);
    static final Duration QUIET_CHAT_WINDOW = Duration.ofDays(60);
    static final Duration ONBOARDING_AFTER = Duration.ofHours(24);
    static final Duration ONBOARDING_WINDOW = Duration.ofDays(14);
    static final Duration KEEP_READ = Duration.ofDays(90);
    private static final ZoneId ZONE = ZoneId.of("Africa/Lagos");

    private final MongoTemplate mongoTemplate;
    private final NotificationService notificationService;
    private final Notifications notifications;
    private final InAppNotifications inApp;

    /** A chat message nobody has read or answered for 30 minutes: email the recipient once per message. */
    @Scheduled(fixedDelayString = "${notifications.unreplied-check-ms:300000}", initialDelay = 60000)
    public void unrepliedMessages() {
        Instant now = Instant.now();
        List<Conversation> waiting = mongoTemplate.find(query(where("lastMessage.type").is(Constants.MessageType.TEXT)
                .and("lastMessageAt").gte(Date.from(now.minus(UNREPLIED_WINDOW))).lte(Date.from(now.minus(UNREPLIED_AFTER)))),
                Conversation.class);
        for (Conversation conversation : waiting) {
            run("unreplied " + conversation.getId(), () -> {
                var last = conversation.getLastMessage();
                String recipient = conversation.otherParticipant(last.getSenderId());
                if (conversation.readStateOf(recipient).getUnread() <= 0) return;
                ConversationBook book = conversation.book(last.getBookId())
                        .orElse(conversation.getBooks().isEmpty() ? null : conversation.getBooks().get(0));
                notificationService.send(notifications.unrepliedMessage(recipient, last.getSenderId(),
                        Notifications.snapshot(book), conversation.getId(), last.getPreview(), last.getId()));
            });
        }
    }

    /** Listings, drafts, quiet chats, onboarding, and clearing out old notifications. 09:00 Lagos time by default. */
    @Scheduled(cron = "${notifications.daily-cron:0 0 9 * * *}", zone = "Africa/Lagos")
    public void daily() {
        run("stale listings", this::staleListings);
        run("draft reminders", this::draftReminders);
        run("mark-as-sold nudges", this::markAsSoldNudges);
        run("onboarding reminders", this::onboardingReminders);
        run("retention", () -> {
            long removed = inApp.deleteReadBefore(Date.from(Instant.now().minus(KEEP_READ)));
            if (removed > 0) log.info("Deleted {} read notifications older than {} days", removed, KEEP_READ.toDays());
        });
    }

    /** ACTIVE for 30+ days with no enquiry in the last 30 days; at most once a month per book. */
    void staleListings() {
        Date cutoff = Date.from(Instant.now().minus(STALE_AFTER));
        String month = YearMonth.now(ZONE).toString();
        for (Book book : mongoTemplate.find(query(where("status").is(Constants.BookStatus.ACTIVE)
                .and("createdAt").lte(cutoff)), Book.class)) {
            if (!isMember(book.getUser()) || inApp.hadEnquirySince(book.getId(), cutoff)) continue;
            notificationService.send(notifications.staleListing(book.getUser().getId(), BookSnapshot.of(book), month));
        }
    }

    /** Drafts untouched for 3 days; once per draft. */
    void draftReminders() {
        Date cutoff = Date.from(Instant.now().minus(DRAFT_AFTER));
        for (Book book : mongoTemplate.find(query(where("status").is(Constants.BookStatus.DRAFT)
                .and("updatedAt").lte(cutoff)), Book.class)) {
            if (!isMember(book.getUser())) continue;
            notificationService.send(notifications.draftReminder(book.getUser().getId(), BookSnapshot.of(book)));
        }
    }

    /** An ACTIVE book whose chat went quiet a week ago: did it sell? At most once a month per book. */
    void markAsSoldNudges() {
        Instant now = Instant.now();
        Date quietSince = Date.from(now.minus(QUIET_CHAT_AFTER));
        Date notBefore = Date.from(now.minus(QUIET_CHAT_WINDOW));
        Set<String> bookIds = new HashSet<>();
        for (Conversation c : mongoTemplate.find(query(where("books").elemMatch(
                where("state").is(Constants.ConversationBookState.OPEN)
                        .and("lastMessageAt").lte(quietSince).gte(notBefore))), Conversation.class)) {
            for (ConversationBook b : c.getBooks()) {
                if (b.getState() == Constants.ConversationBookState.OPEN && b.getLastMessageAt() != null
                        && !b.getLastMessageAt().after(quietSince) && !b.getLastMessageAt().before(notBefore)) {
                    bookIds.add(b.getBookId());
                }
            }
        }
        if (bookIds.isEmpty()) return;
        String month = YearMonth.now(ZONE).toString();
        for (Book book : mongoTemplate.find(query(where("_id").in(bookIds).and("status").is(Constants.BookStatus.ACTIVE)),
                Book.class)) {
            if (!isMember(book.getUser())) continue;
            notificationService.send(notifications.markAsSoldNudge(book.getUser().getId(), BookSnapshot.of(book), month));
        }
    }

    /** Verified accounts, 1 to 14 days old, that haven't finished onboarding; once per user. */
    void onboardingReminders() {
        Instant now = Instant.now();
        Criteria criteria = where("isEmailVerified").is(true).and("isDeleted").is(false)
                .and("role").ne(Constants.ROLE_SUPER_ADMIN)
                .and("createdAt").lte(Date.from(now.minus(ONBOARDING_AFTER))).gte(Date.from(now.minus(ONBOARDING_WINDOW)));
        for (User user : mongoTemplate.find(query(criteria), User.class)) {
            Profile profile = user.getProfile();
            if (profile != null && profile.isOnboardingCompleted()) continue;
            notificationService.send(notifications.onboardingReminder(user.getId()));
        }
    }

    /** Reminders are for marketplace members, not the admin account (which owns the seeded books). */
    private static boolean isMember(User user) {
        return user != null && !Constants.ROLE_SUPER_ADMIN.equals(user.getRole());
    }

    private static void run(String name, Runnable job) {
        try {
            job.run();
        } catch (Exception e) {
            log.error("Notification job '{}' failed", name, e);
        }
    }
}

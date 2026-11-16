package com.personal.rebooked.notification;

import java.util.EnumSet;
import java.util.Set;

import static com.personal.rebooked.notification.NotificationChannel.EMAIL;
import static com.personal.rebooked.notification.NotificationChannel.IN_APP;

/**
 * Every notification the platform sends, with the channels it goes out on by default.
 * Keep docs/notification.md in step with this list.
 */
public enum NotificationType {
    // Account
    EMAIL_VERIFICATION(Category.ACCOUNT, true, EMAIL),
    PASSWORD_RESET(Category.ACCOUNT, true, EMAIL),
    PASSWORD_CHANGED(Category.ACCOUNT, true, EMAIL),
    WELCOME(Category.ACCOUNT, false, IN_APP, EMAIL),
    ONBOARDING_REMINDER(Category.ACCOUNT, false, EMAIL),

    // Chat
    BOOK_ENQUIRY(Category.CHAT, false, IN_APP, EMAIL),
    NEW_MESSAGES(Category.CHAT, false, IN_APP),
    UNREPLIED_MESSAGE(Category.CHAT, false, EMAIL),

    // Sales
    SOLD_TO_YOU(Category.SALES, false, IN_APP, EMAIL),
    SOLD_ELSEWHERE(Category.SALES, false, IN_APP),
    BOOK_UNAVAILABLE(Category.SALES, false, IN_APP),
    BOOK_AVAILABLE(Category.SALES, false, IN_APP, EMAIL),
    MARK_AS_SOLD_NUDGE(Category.SALES, false, IN_APP, EMAIL),
    /** Ask the buyer to rate the seller. The 3-day reminder is the same type, email only. */
    RATE_SELLER(Category.SALES, false, IN_APP, EMAIL),

    // Listings
    STALE_LISTING(Category.LISTINGS, false, IN_APP, EMAIL),
    DRAFT_REMINDER(Category.LISTINGS, false, IN_APP, EMAIL),
    VIEWS_MILESTONE(Category.LISTINGS, false, IN_APP);

    public enum Category { ACCOUNT, CHAT, SALES, LISTINGS }

    private final Category category;
    private final boolean mandatory;
    private final Set<NotificationChannel> defaultChannels;

    NotificationType(Category category, boolean mandatory, NotificationChannel first, NotificationChannel... rest) {
        this.category = category;
        this.mandatory = mandatory;
        this.defaultChannels = EnumSet.of(first, rest);
    }

    public Category category() {
        return category;
    }

    /** Can't be turned off, and is delivered synchronously so a failure reaches the caller. */
    public boolean mandatory() {
        return mandatory;
    }

    public Set<NotificationChannel> defaultChannels() {
        return EnumSet.copyOf(defaultChannels);
    }
}

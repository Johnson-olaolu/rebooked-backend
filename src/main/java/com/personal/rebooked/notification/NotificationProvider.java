package com.personal.rebooked.notification;

import com.personal.rebooked.user.models.User;

/**
 * Delivers notifications on one channel. Implement this as a Spring bean to add a channel
 * (push, WhatsApp, ...); {@link ProviderRegistry} picks it up automatically.
 */
public interface NotificationProvider {
    NotificationChannel channel();

    /** Whether this provider can render the type; unsupported types are skipped and logged. */
    default boolean supports(NotificationType type) {
        return true;
    }

    /** Throw to mark the delivery FAILED. Return normally once it's sent. */
    void deliver(NotificationRequest request, User recipient) throws Exception;
}

package com.personal.rebooked.notification;

/**
 * Where a notification can be delivered. Adding a channel (PUSH, WHATSAPP, SMS, ...) means adding a value here
 * and a {@link NotificationProvider} bean for it; senders don't change.
 */
public enum NotificationChannel {
    IN_APP,
    EMAIL
}

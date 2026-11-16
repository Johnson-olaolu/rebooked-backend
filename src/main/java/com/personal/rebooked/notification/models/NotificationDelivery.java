package com.personal.rebooked.notification.models;

import com.personal.rebooked.notification.NotificationChannel;
import com.personal.rebooked.notification.NotificationType;
import lombok.Data;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.util.Date;

/**
 * One attempt to deliver a notification on one channel: the audit trail, and the dedupe store
 * (a partial unique index on { channel, dedupeKey }, created in NotificationService). Kept 90 days.
 */
@Data
@Document("notification_deliveries")
public class NotificationDelivery {
    public enum Status { PENDING, SENT, SKIPPED, FAILED }

    @Id
    private String id;

    @Indexed
    private String userId;

    private NotificationType type;

    private NotificationChannel channel;

    private String dedupeKey;

    private Status status;

    private String error;

    @Indexed(name = "ttl_90_days", expireAfter = "90d")
    private Date createdAt;

    private Date updatedAt;
}

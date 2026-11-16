package com.personal.rebooked.notification.models;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.personal.rebooked.notification.ActorSnapshot;
import com.personal.rebooked.notification.BookSnapshot;
import com.personal.rebooked.notification.NotificationType;
import lombok.Data;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.index.CompoundIndexes;
import org.springframework.data.mongodb.core.mapping.Document;

import java.util.Date;

/**
 * An in-app notification. The unique index on { userId, openGroupKey } is partial, so it's created in
 * InAppProvider: at most one unread notification per group (e.g. NEW_MESSAGES for one conversation).
 */
@Data
@Document("notifications")
@CompoundIndexes({
        @CompoundIndex(name = "user_updated", def = "{'userId': 1, 'updatedAt': -1}"),
        @CompoundIndex(name = "user_read", def = "{'userId': 1, 'readAt': 1}")
})
public class Notification {
    @Id
    private String id;

    @JsonIgnore
    private String userId;

    private NotificationType type;

    /** Who caused it; null for reminders. */
    private ActorSnapshot actor;

    private BookSnapshot book;

    private String conversationId;

    /** NEW_MESSAGES: how many messages it groups. Otherwise 1. */
    private int count = 1;

    /** NEW_MESSAGES: the latest message, at most 140 characters. */
    private String preview;

    @JsonIgnore
    private String groupKey;

    /** Equal to groupKey while unread, removed once read, so the next message starts a new notification. */
    @JsonIgnore
    private String openGroupKey;

    private Date readAt;

    private Date createdAt;

    /** Bumped whenever a grouped notification takes another message. */
    private Date updatedAt;

    @JsonProperty("read")
    public boolean isRead() {
        return readAt != null;
    }
}

package com.personal.rebooked.notification;

import lombok.Builder;

import java.util.Map;
import java.util.Set;

/**
 * One notification for one user. Build these with {@link Notifications}, then pass them to
 * {@link NotificationService#send}.
 *
 * @param groupKey  in-app: merge into the user's open (unread) notification with this key, e.g. NEW_MESSAGES:<id>
 * @param dedupeKey per channel: never deliver twice with this key, e.g. UNREPLIED:<messageId>
 * @param data      extra values for the channel's content, e.g. the verification code
 * @param channels  null means the type's default channels
 */
@Builder(toBuilder = true)
public record NotificationRequest(NotificationType type, String recipientId, ActorSnapshot actor, BookSnapshot book,
                                  String conversationId, String preview, String groupKey, String dedupeKey,
                                  Map<String, Object> data, Set<NotificationChannel> channels) {
    public Object data(String key) {
        return data == null ? null : data.get(key);
    }
}

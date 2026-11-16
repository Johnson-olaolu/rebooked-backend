package com.personal.rebooked.notification.models;

import com.personal.rebooked.notification.NotificationChannel;
import com.personal.rebooked.notification.NotificationType;
import lombok.Data;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.util.EnumMap;
import java.util.EnumSet;
import java.util.Map;
import java.util.Set;

/** What a user has turned off. No document, or no entry for a type, means everything is on. */
@Data
@Document("notification_preferences")
public class NotificationPreferences {
    @Id
    private String id;

    @Indexed(unique = true)
    private String userId;

    private Map<NotificationType, Set<NotificationChannel>> disabled = new EnumMap<>(NotificationType.class);

    public boolean isEnabled(NotificationType type, NotificationChannel channel) {
        Set<NotificationChannel> off = disabled == null ? null : disabled.get(type);
        return off == null || !off.contains(channel);
    }

    public void set(NotificationType type, NotificationChannel channel, boolean enabled) {
        if (disabled == null) disabled = new EnumMap<>(NotificationType.class);
        Set<NotificationChannel> off = disabled.computeIfAbsent(type, t -> EnumSet.noneOf(NotificationChannel.class));
        if (enabled) off.remove(channel); else off.add(channel);
        if (off.isEmpty()) disabled.remove(type);
    }
}

package com.personal.rebooked.notification;

import com.personal.rebooked.notification.models.Notification;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Notification events on the same private queue as chat (/user/queue/events). */
public final class NotificationEvents {
    private NotificationEvents() {}

    public static Map<String, Object> notification(Notification notification, long unreadTotal) {
        Map<String, Object> event = new LinkedHashMap<>();
        event.put("type", "notification");
        event.put("notification", notification);
        event.put("unreadTotal", unreadTotal);
        return event;
    }

    public static Map<String, Object> read(List<String> ids, boolean all, long unreadTotal) {
        Map<String, Object> event = new LinkedHashMap<>();
        event.put("type", "notification.read");
        event.put("ids", ids);
        event.put("all", all);
        event.put("unreadTotal", unreadTotal);
        return event;
    }
}

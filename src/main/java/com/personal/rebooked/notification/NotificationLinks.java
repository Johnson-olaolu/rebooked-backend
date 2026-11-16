package com.personal.rebooked.notification;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

/** Frontend URLs used in emails. All in one place so they're easy to change if a route moves. */
@Component
public class NotificationLinks {
    private final String base;

    public NotificationLinks(@Value("${client.data-url}") String clientUrl) {
        this.base = clientUrl.endsWith("/") ? clientUrl.substring(0, clientUrl.length() - 1) : clientUrl;
    }

    public String home() {
        return base + "/";
    }

    public String welcome() {
        return base + "/welcome";
    }

    public String conversation(String conversationId) {
        return conversationId == null ? messages() : base + "/dashboard/messages/" + conversationId;
    }

    public String messages() {
        return base + "/dashboard/messages";
    }

    public String notifications() {
        return base + "/dashboard/notifications";
    }

    public String shelf() {
        return base + "/dashboard/shelf";
    }

    public String book(BookSnapshot book) {
        if (book == null) return home();
        return base + "/book/" + (book.slug() != null ? book.slug() : book.id());
    }

    public String editBook(BookSnapshot book) {
        return book == null ? shelf() : base + "/dashboard/shelf/" + book.id() + "/edit";
    }

    public String changePassword(String email, String token) {
        return base + "/auth/change-password?email=" + encode(email) + "&token=" + encode(token);
    }

    public String unsubscribe(String token) {
        return base + "/unsubscribe?token=" + encode(token);
    }

    public String preferences() {
        return base + "/dashboard/settings/notifications";
    }

    private static String encode(String value) {
        return URLEncoder.encode(value == null ? "" : value, StandardCharsets.UTF_8);
    }
}

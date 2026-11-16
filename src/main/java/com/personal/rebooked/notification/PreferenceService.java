package com.personal.rebooked.notification;

import com.personal.rebooked.exception.ApiException;
import com.personal.rebooked.notification.models.NotificationPreferences;
import com.personal.rebooked.notification.repositories.NotificationPreferencesRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.util.*;

/** Per-user, per-type, per-channel opt-outs. Mandatory types can't be turned off. */
@Service
@RequiredArgsConstructor
public class PreferenceService {
    public record TypePreference(NotificationType type, NotificationType.Category category,
                                 Map<NotificationChannel, Boolean> channels) {}

    private final NotificationPreferencesRepository repository;

    public boolean isEnabled(String userId, NotificationType type, NotificationChannel channel) {
        if (type.mandatory()) return true;
        return repository.findByUserId(userId).map(p -> p.isEnabled(type, channel)).orElse(true);
    }

    /** Every type a user can turn off, with each of its channels on or off. */
    public List<TypePreference> list(String userId) {
        NotificationPreferences prefs = repository.findByUserId(userId).orElseGet(NotificationPreferences::new);
        List<TypePreference> result = new ArrayList<>();
        for (NotificationType type : NotificationType.values()) {
            if (type.mandatory()) continue;
            Map<NotificationChannel, Boolean> channels = new EnumMap<>(NotificationChannel.class);
            for (NotificationChannel channel : type.defaultChannels()) channels.put(channel, prefs.isEnabled(type, channel));
            result.add(new TypePreference(type, type.category(), channels));
        }
        return result;
    }

    public List<TypePreference> update(String userId, NotificationType type, NotificationChannel channel, Boolean enabled) {
        Map<String, String> errors = new LinkedHashMap<>();
        if (type == null) errors.put("type", "Type is required");
        if (channel == null) errors.put("channel", "Channel is required");
        if (enabled == null) errors.put("enabled", "enabled is required");
        if (type != null && type.mandatory()) errors.put("type", "Account emails can't be turned off");
        if (type != null && channel != null && !type.mandatory() && !type.defaultChannels().contains(channel)) {
            errors.put("channel", type + " isn't sent by " + channel);
        }
        if (!errors.isEmpty()) throw new ApiException(HttpStatus.BAD_REQUEST, "Validation failed", errors);
        set(userId, type, channel, enabled);
        return list(userId);
    }

    void set(String userId, NotificationType type, NotificationChannel channel, boolean enabled) {
        NotificationPreferences prefs = repository.findByUserId(userId).orElseGet(() -> {
            NotificationPreferences p = new NotificationPreferences();
            p.setUserId(userId);
            return p;
        });
        prefs.set(type, channel, enabled);
        try {
            repository.save(prefs);
        } catch (DuplicateKeyException e) {
            // Created concurrently; apply the change to the stored copy
            NotificationPreferences stored = repository.findByUserId(userId).orElseThrow();
            stored.set(type, channel, enabled);
            repository.save(stored);
        }
    }
}

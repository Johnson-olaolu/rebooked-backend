package com.personal.rebooked.notification.repositories;

import com.personal.rebooked.notification.models.NotificationPreferences;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.Optional;

public interface NotificationPreferencesRepository extends MongoRepository<NotificationPreferences, String> {
    Optional<NotificationPreferences> findByUserId(String userId);
}

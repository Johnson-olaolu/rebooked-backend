package com.personal.rebooked.notification.repositories;

import com.personal.rebooked.notification.models.Notification;
import org.springframework.data.mongodb.repository.MongoRepository;

public interface NotificationRepository extends MongoRepository<Notification, String> {
    long countByUserIdAndReadAtIsNull(String userId);
}

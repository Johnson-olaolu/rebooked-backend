package com.personal.rebooked.conversation.repositories;

import com.personal.rebooked.conversation.models.Message;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.Optional;

public interface MessageRepository extends MongoRepository<Message, String> {
    Optional<Message> findBySenderIdAndClientId(String senderId, String clientId);
}

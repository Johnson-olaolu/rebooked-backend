package com.personal.rebooked.chat.repositories;

import com.personal.rebooked.chat.models.Message;
import org.springframework.data.mongodb.repository.MongoRepository;

public interface MessageRepository extends MongoRepository<Message, String> {
}

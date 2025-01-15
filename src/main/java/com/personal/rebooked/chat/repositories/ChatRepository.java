package com.personal.rebooked.chat.repositories;

import com.personal.rebooked.chat.models.Chat;
import org.springframework.data.mongodb.repository.MongoRepository;

public interface ChatRepository  extends MongoRepository<Chat, String> {
}

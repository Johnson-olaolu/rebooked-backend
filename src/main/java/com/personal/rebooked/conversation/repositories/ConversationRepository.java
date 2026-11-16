package com.personal.rebooked.conversation.repositories;

import com.personal.rebooked.conversation.models.Conversation;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.List;
import java.util.Optional;

public interface ConversationRepository extends MongoRepository<Conversation, String> {
    Optional<Conversation> findByPairKey(String pairKey);

    List<Conversation> findByBooksBookId(String bookId);
}

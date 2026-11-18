package com.personal.rebooked.author.repositories;

import com.personal.rebooked.author.models.AuthorReviewItem;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.List;

public interface AuthorReviewItemRepository extends MongoRepository<AuthorReviewItem, String> {
    boolean existsByPairKeyAndResolution(String pairKey, AuthorReviewItem.Resolution resolution);

    boolean existsByAuthorIdAndTypeAndStatus(String authorId, AuthorReviewItem.Type type, AuthorReviewItem.ItemStatus status);

    List<AuthorReviewItem> findByAuthorIdAndStatus(String authorId, AuthorReviewItem.ItemStatus status);

    List<AuthorReviewItem> findByOtherAuthorIdAndStatus(String otherAuthorId, AuthorReviewItem.ItemStatus status);
}

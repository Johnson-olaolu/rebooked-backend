package com.personal.rebooked.book.saved;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.repository.MongoRepository;

public interface SavedBookRepository extends MongoRepository<SavedBook, String> {
    boolean existsByUserIdAndBookId(String userId, String bookId);

    long countByUserId(String userId);

    Page<SavedBook> findByUserIdOrderByCreatedAtDesc(String userId, Pageable pageable);

    long deleteByUserIdAndBookId(String userId, String bookId);

    long deleteByBookId(String bookId);
}

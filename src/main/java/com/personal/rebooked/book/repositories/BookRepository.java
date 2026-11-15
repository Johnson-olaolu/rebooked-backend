package com.personal.rebooked.book.repositories;

import com.personal.rebooked.book.models.Book;
import com.personal.rebooked.utils.Constants;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.List;
import java.util.Optional;

public interface BookRepository extends MongoRepository<Book, String> {

    List<Book> findByStatus(Constants.BookStatus status);

    Optional<Book> findBySlug(String slug);

    boolean existsBySlug(String slug);



    List<Book> findTop10ByStatusOrderByViewCountDesc( Constants.BookStatus status);
}

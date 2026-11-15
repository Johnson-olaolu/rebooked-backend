package com.personal.rebooked.book.repositories;

import com.personal.rebooked.book.models.Book;
import com.personal.rebooked.utils.Constants;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.List;

public interface BookRepository extends MongoRepository<Book, String> {

    List<Book> findByStatus(Constants.BookStatus status);



    List<Book> findTop10ByStatusOrderByViewCountDesc( Constants.BookStatus status);
}

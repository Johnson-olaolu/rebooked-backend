package com.personal.rebooked.book.repositories;

import com.personal.rebooked.book.models.Book;
import com.personal.rebooked.utils.Constants;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.data.mongodb.repository.Query;

import java.util.Date;
import java.util.List;

public interface BookRepository extends MongoRepository<Book, String> {

    List<Book> findByStatus(Constants.BookStatus status);


    @Query("{ $and: [ " +
            "{ $or: [ { 'user.id': ?0 }, { ?0: null } ] },  " +
            "{ 'soldDate': { $gte: ?1, $lte: ?2 } }, " +
            "{'status' :  ?3}" +
            "] }")
    List<Book> findBySoldDateBetween(String userId, Date startDate, Date endDate, Constants.BookStatus status);
}

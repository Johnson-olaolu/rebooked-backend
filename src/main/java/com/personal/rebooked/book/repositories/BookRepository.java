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


    @Query("{ $and :  [" +
            "{ $or: [ { 'user.id': ?0 }, { ?0: null } ] }, " +
            "{ $or: [ { 'categories': { $in: ?1 } }, { ?1: { $size: 0 } } ] }, " +
            "{ $or: [ { 'title': { $regex: ?2, $options: 'i' } }, { 'author': { $regex: ?2, $options: 'i' } }, { 'categories.name': { $regex: ?2, $options: 'i' } } ] }" +
            "] }")
    Page<Book> findByTitleAuthorOrCategoryName(String userId, List<String>categoryIds, String query, Pageable pageable);


    @Query("{ $and: [ " +
            "{ $or: [ { 'user.id': ?0 }, { ?0: null } ] }, " +
            "{'status' :  ?3}," +
            "{ 'soldDate': { $gte: ?1, $lte: ?2 } } " +
            "] }")
    List<Book> findBySoldDateBetween(String userId, Date startDate, Date endDate, Constants.BookStatus status);
}

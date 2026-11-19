package com.personal.rebooked.homepage;

import com.personal.rebooked.book.BookService;
import com.personal.rebooked.book.models.Book;
import com.personal.rebooked.utils.Constants;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.function.Predicate;

import static org.springframework.data.mongodb.core.query.Criteria.where;

/** Book reads shared by the sections. Snapshots hold ids; these turn them back into live ACTIVE books. */
@Component
@RequiredArgsConstructor
public class HomepageBooks {
    private final BookService bookService;
    private final MongoTemplate mongoTemplate;

    /** The ACTIVE books among {@code ids}, in that order. */
    public List<Book> active(List<String> ids) {
        if (ids == null || ids.isEmpty()) return new ArrayList<>();
        return new ArrayList<>(bookService.booksInOrder(ids).stream().filter(HomepageBooks::isActive).toList());
    }

    /** The newest ACTIVE books (the shop's NEWEST order), optionally only those with a cover. */
    public List<Book> newest(int limit, boolean withCover, Collection<String> excludeIds) {
        Criteria criteria = where("status").is(Constants.BookStatus.ACTIVE);
        if (withCover) criteria = criteria.and("coverImage").ne(null);
        if (excludeIds != null && !excludeIds.isEmpty()) criteria = criteria.and("_id").nin(excludeIds);
        Query query = new Query(criteria).with(Sort.by(Sort.Direction.DESC, "createdAt")).limit(limit);
        return mongoTemplate.find(query, Book.class);
    }

    /** ACTIVE books by all-time views (GET /book/most-viewed). */
    public List<Book> topAllTime() {
        return bookService.getTopBooks();
    }

    public static boolean isActive(Book book) {
        return book != null && book.getStatus() == Constants.BookStatus.ACTIVE;
    }

    public static boolean hasCover(Book book) {
        return book != null && book.getCoverImage() != null;
    }

    /** Adds books from {@code source} that pass {@code filter} and aren't already in {@code books}, up to {@code target}. */
    public static void fill(List<Book> books, int target, List<Book> source, Predicate<Book> filter) {
        if (books.size() >= target || source == null) return;
        Set<String> seen = new HashSet<>();
        books.forEach(b -> seen.add(b.getId()));
        for (Book book : source) {
            if (books.size() >= target) break;
            if (book != null && filter.test(book) && seen.add(book.getId())) books.add(book);
        }
    }

    public static List<String> ids(List<Book> books) {
        return books.stream().map(Book::getId).toList();
    }
}

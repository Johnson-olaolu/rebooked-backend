package com.personal.rebooked.book.saved;

import com.personal.rebooked.book.BookChangedEvent;
import com.personal.rebooked.book.BookService;
import com.personal.rebooked.book.models.Book;
import com.personal.rebooked.book.repositories.BookRepository;
import com.personal.rebooked.exception.ApiException;
import com.personal.rebooked.utils.Constants;
import lombok.RequiredArgsConstructor;
import org.bson.types.ObjectId;
import org.springframework.context.event.EventListener;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.util.*;

@Service
@RequiredArgsConstructor
public class SavedBookService {

    public static final int MAX_SAVED = 500;
    private static final int DEFAULT_PAGE_SIZE = 24;
    private static final int MAX_PAGE_SIZE = 60;

    private final SavedBookRepository savedBookRepository;
    private final BookRepository bookRepository;
    private final BookService bookService;
    private final MongoTemplate mongoTemplate;

    /** PUT /book/{id}/save. Saving again is fine. */
    public void save(String userId, String bookId) {
        Book book = ObjectId.isValid(bookId) ? bookRepository.findById(bookId).orElse(null) : null;
        if (book == null || book.getStatus() == Constants.BookStatus.DRAFT) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Book not found");
        }
        if (book.getUser() != null && userId.equals(book.getUser().getId())) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "That's your own book", Map.of("book", "That's your own book"));
        }
        if (savedBookRepository.existsByUserIdAndBookId(userId, bookId)) return;
        if (savedBookRepository.countByUserId(userId) >= MAX_SAVED) {
            String message = "You can save up to " + MAX_SAVED + " books";
            throw new ApiException(HttpStatus.BAD_REQUEST, message, Map.of("book", message));
        }
        try {
            savedBookRepository.insert(new SavedBook(null, userId, bookId, new Date()));
        } catch (DuplicateKeyException e) {
            // A double click raced us; it's saved either way
        }
    }

    /** DELETE /book/{id}/save. Removing one that isn't saved is fine. */
    public void unsave(String userId, String bookId) {
        savedBookRepository.deleteByUserIdAndBookId(userId, bookId);
    }

    /** GET /book/saved: newest saved first, any status but DRAFT (the frontend badges Sold and Not available). */
    public SavedBooksPage list(String userId, Integer page, Integer pageSize) {
        int p = page == null || page < 0 ? 0 : page;
        int size = pageSize == null || pageSize < 1 ? DEFAULT_PAGE_SIZE : Math.min(pageSize, MAX_PAGE_SIZE);
        Page<SavedBook> saved = savedBookRepository.findByUserIdOrderByCreatedAtDesc(userId, PageRequest.of(p, size));
        List<String> ids = saved.getContent().stream().map(SavedBook::getBookId).toList();
        List<Book> books = bookService.booksInOrder(ids).stream()
                .filter(b -> b.getStatus() != Constants.BookStatus.DRAFT)
                .toList();
        return new SavedBooksPage(books, p, size, saved.getTotalElements(), saved.getTotalPages());
    }

    /** GET /book/saved/ids: every saved bookId, to fill in the hearts. */
    public List<String> ids(String userId) {
        Query query = Query.query(Criteria.where("userId").is(userId));
        query.fields().include("bookId").exclude("_id");
        return mongoTemplate.find(query, SavedBook.class).stream().map(SavedBook::getBookId).toList();
    }

    @EventListener
    public void onBookChanged(BookChangedEvent event) {
        if (event.kind() == BookChangedEvent.Kind.DELETED) {
            savedBookRepository.deleteByBookId(event.book().getId());
        }
    }
}

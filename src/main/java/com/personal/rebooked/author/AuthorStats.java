package com.personal.rebooked.author;

import com.personal.rebooked.author.models.Author;
import com.personal.rebooked.book.models.Book;
import com.personal.rebooked.book.models.BookAuthor;
import com.personal.rebooked.utils.Constants;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.stereotype.Component;

import java.util.*;

import static org.springframework.data.mongodb.core.query.Criteria.where;
import static org.springframework.data.mongodb.core.query.Query.query;

/**
 * availableCount and soldCount on authors, stored on write (§23a). Recounts rather than increments, so the
 * numbers can't drift. Depends on nothing but Mongo, so BookService and ConversationService can both use it.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AuthorStats {
    private final MongoTemplate mongoTemplate;

    /** Recount the given authors (usually the 1–10 on one book). Never fails the write that triggered it. */
    public void recount(Collection<String> authorIds) {
        if (authorIds == null || authorIds.isEmpty()) return;
        for (String id : new LinkedHashSet<>(authorIds)) {
            if (id == null) continue;
            try {
                long available = mongoTemplate.count(query(where("authors.authorId").is(id)
                        .and("status").is(Constants.BookStatus.ACTIVE)), Book.class);
                long sold = mongoTemplate.count(query(where("authors.authorId").is(id)
                        .and("status").is(Constants.BookStatus.SOLD)), Book.class);
                mongoTemplate.updateFirst(query(where("id").is(id)),
                        new Update().set("availableCount", available).set("soldCount", sold), Author.class);
            } catch (Exception e) {
                log.error("Couldn't recount author {}; it will be fixed by the next write", id, e);
            }
        }
    }

    public void recountBook(Book book) {
        recount(idsOf(book));
    }

    public static List<String> idsOf(Book book) {
        if (book == null || book.getAuthors() == null) return List.of();
        return book.getAuthors().stream().map(BookAuthor::getAuthorId).filter(Objects::nonNull).toList();
    }
}

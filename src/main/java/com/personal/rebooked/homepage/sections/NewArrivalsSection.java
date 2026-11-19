package com.personal.rebooked.homepage.sections;

import com.fasterxml.jackson.core.type.TypeReference;
import com.personal.rebooked.book.BookChangedEvent;
import com.personal.rebooked.book.models.Book;
import com.personal.rebooked.homepage.HomepageBooks;
import com.personal.rebooked.homepage.HomepageCache;
import com.personal.rebooked.utils.Constants;
import lombok.RequiredArgsConstructor;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.List;

/** §24c new arrivals: the 12 newest ACTIVE books. 5-minute snapshot, evicted when a book enters or leaves ACTIVE. */
@Component
@RequiredArgsConstructor
public class NewArrivalsSection {
    public static final String KEY = "homepage:new";
    static final Duration TTL = Duration.ofMinutes(5);
    static final int TARGET = 12;

    private final HomepageCache cache;
    private final HomepageBooks books;

    public List<Book> read() {
        List<String> ids = cache.get(KEY, new TypeReference<List<String>>() {}).orElse(null);
        if (ids == null) return build();
        List<Book> newest = books.active(ids);
        // One sold or was unpublished without an event (e.g. sold from chat): the query is cheap, rebuild
        return newest.size() < ids.size() ? build() : newest;
    }

    public List<Book> build() {
        List<Book> newest = books.newest(TARGET, false, null);
        cache.put(KEY, HomepageBooks.ids(newest), TTL);
        return newest;
    }

    @EventListener
    public void onBookChanged(BookChangedEvent event) {
        Constants.BookStatus now = event.book() == null ? null : event.book().getStatus();
        boolean entersOrLeaves = event.kind() == BookChangedEvent.Kind.DELETED
                ? event.previousStatus() == Constants.BookStatus.ACTIVE
                : (now == Constants.BookStatus.ACTIVE) != (event.previousStatus() == Constants.BookStatus.ACTIVE);
        if (entersOrLeaves) cache.evict(KEY);
    }
}

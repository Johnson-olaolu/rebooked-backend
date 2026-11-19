package com.personal.rebooked.homepage.sections;

import com.fasterxml.jackson.core.type.TypeReference;
import com.personal.rebooked.book.models.Book;
import com.personal.rebooked.homepage.HomepageBooks;
import com.personal.rebooked.homepage.HomepageCache;
import com.personal.rebooked.homepage.WeekViews;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

/** §24c trending this week: the most viewed ACTIVE books over 7 days, backfilled from all-time views. Hourly. */
@Component
@RequiredArgsConstructor
public class TrendingSection {
    public static final String KEY = "homepage:trending";
    static final Duration TTL = Duration.ofHours(2);
    public static final int TARGET = 12;
    static final int MIN = 6;
    private static final int BATCH = 50;
    private static final int MAX_SCANNED = 500;

    private final HomepageCache cache;
    private final HomepageBooks books;
    private final WeekViews weekViews;

    public List<Book> read() {
        List<String> ids = cache.get(KEY, new TypeReference<List<String>>() {}).orElse(null);
        if (ids == null) return build();
        List<Book> trending = books.active(ids);
        // Some sold since the snapshot
        if (trending.size() < MIN) HomepageBooks.fill(trending, TARGET, books.topAllTime(), HomepageBooks::isActive);
        return trending;
    }

    /** Rebuilds views:week, then the snapshot. */
    public List<Book> build() {
        weekViews.rebuild();
        List<Book> trending = new ArrayList<>();
        for (int start = 0; start < MAX_SCANNED && trending.size() < TARGET; start += BATCH) {
            List<String> ranked = weekViews.ranked(start, BATCH);
            if (ranked.isEmpty()) break;
            for (Book book : books.active(ranked)) {
                if (trending.size() >= TARGET) break;
                trending.add(book);
            }
        }
        if (trending.size() < MIN) HomepageBooks.fill(trending, TARGET, books.topAllTime(), HomepageBooks::isActive);
        cache.put(KEY, HomepageBooks.ids(trending), TTL);
        return trending;
    }
}

package com.personal.rebooked.homepage.sections;

import com.fasterxml.jackson.core.type.TypeReference;
import com.personal.rebooked.author.AuthorService;
import com.personal.rebooked.author.dto.AuthorSummary;
import com.personal.rebooked.author.models.Author;
import com.personal.rebooked.book.models.Book;
import com.personal.rebooked.homepage.HomepageCache;
import com.personal.rebooked.homepage.HomepageWeek;
import com.personal.rebooked.homepage.WeekViews;
import com.personal.rebooked.homepage.dto.ScoredAuthor;
import com.personal.rebooked.homepage.dto.TopAuthorView;
import com.personal.rebooked.utils.Constants;
import lombok.RequiredArgsConstructor;
import org.bson.Document;
import org.bson.conversions.Bson;
import org.bson.types.ObjectId;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Duration;
import java.time.LocalDate;
import java.util.*;

import static com.mongodb.client.model.Filters.*;
import static com.mongodb.client.model.Projections.include;
import static org.springframework.data.mongodb.core.query.Criteria.where;

/**
 * §24d top authors of the week:
 * weekScore = Σ 7-day views of the author's books + 5 × their books sold in 7 days + 2 × their books listed in 7 days.
 * Only authors with an ACTIVE book, never MERGED. Snapshot on Monday, refreshed daily; under 3 qualifying authors
 * falls back to the most-shelved list (GET /author/top, which itself is unchanged).
 */
@Component
@RequiredArgsConstructor
public class TopAuthorsSection {
    static final String KEY_PREFIX = "homepage:top-authors:";
    static final Duration TTL = Duration.ofDays(8);
    static final int LIMIT = 6;
    static final int MIN = 3;
    static final long SOLD_WEIGHT = 5;
    static final long LISTED_WEIGHT = 2;
    private static final int CHUNK = 1000;

    private final HomepageCache cache;
    private final MongoTemplate mongoTemplate;
    private final AuthorService authorService;
    private final WeekViews weekViews;
    private final Clock clock;

    public static String key(LocalDate weekStart) {
        return KEY_PREFIX + weekStart;
    }

    public List<TopAuthorView> read() {
        LocalDate week = HomepageWeek.current(clock);
        List<ScoredAuthor> scored = cache.get(key(week), new TypeReference<List<ScoredAuthor>>() {})
                .orElseGet(() -> build(week));
        Map<String, Author> authors = load(scored.stream().map(ScoredAuthor::authorId).toList());
        List<TopAuthorView> views = new ArrayList<>();
        for (ScoredAuthor s : scored) {
            Author a = authors.get(s.authorId());
            if (a == null || a.getStatus() == Author.Status.MERGED || a.getAvailableCount() <= 0) continue;
            views.add(new TopAuthorView(AuthorSummary.of(a), authorService.detail(a).photoUrl(), s.weekScore()));
        }
        return views;
    }

    public List<ScoredAuthor> build() {
        return build(HomepageWeek.current(clock));
    }

    public List<ScoredAuthor> build(LocalDate week) {
        List<ScoredAuthor> ranked = rankAll();
        List<ScoredAuthor> top = ranked.stream().filter(s -> s.weekScore() > 0).limit(LIMIT).toList();
        if (top.size() < MIN) {
            Map<String, Long> scores = new HashMap<>();
            ranked.forEach(s -> scores.put(s.authorId(), s.weekScore()));
            top = authorService.top(LIMIT).stream()
                    .map(a -> new ScoredAuthor(a.id(), scores.getOrDefault(a.id(), 0L))).toList();
        }
        cache.put(key(week), top, TTL);
        return top;
    }

    /** Every qualifying author with a score this week, best first (ties: more ACTIVE books). The featured job uses it too. */
    public List<ScoredAuthor> rankAll() {
        Date since = HomepageWeek.sevenDaysAgo(clock);
        Map<String, Double> views = weekViews.scores();

        Map<String, List<String>> viewedBookAuthors = new HashMap<>();
        List<String> viewedIds = views.keySet().stream().filter(ObjectId::isValid).toList();
        for (int from = 0; from < viewedIds.size(); from += CHUNK) {
            List<ObjectId> chunk = viewedIds.subList(from, Math.min(from + CHUNK, viewedIds.size()))
                    .stream().map(ObjectId::new).toList();
            for (Document doc : books(in("_id", chunk))) {
                viewedBookAuthors.put(doc.getObjectId("_id").toHexString(), authorIdsOf(doc));
            }
        }
        List<List<String>> sold = new ArrayList<>();
        for (Document doc : books(and(eq("status", Constants.BookStatus.SOLD.name()), gte("soldDate", since)))) {
            sold.add(authorIdsOf(doc));
        }
        List<List<String>> listed = new ArrayList<>();
        for (Document doc : books(and(in("status", Constants.BookStatus.ACTIVE.name(), Constants.BookStatus.SOLD.name()),
                gte("createdAt", since)))) {
            listed.add(authorIdsOf(doc));
        }

        Map<String, Long> scores = score(views, viewedBookAuthors, sold, listed);
        Map<String, Author> authors = load(new ArrayList<>(scores.keySet()));
        Map<String, Long> available = new HashMap<>();
        authors.forEach((id, a) -> {
            if (a.getStatus() != Author.Status.MERGED && a.getAvailableCount() > 0) available.put(id, a.getAvailableCount());
        });
        return rank(scores, available);
    }

    /** The weekScore of every author that appears in any of the inputs. */
    static Map<String, Long> score(Map<String, Double> bookViews, Map<String, List<String>> bookAuthors,
                                   List<List<String>> soldBookAuthors, List<List<String>> listedBookAuthors) {
        Map<String, Long> scores = new HashMap<>();
        bookAuthors.forEach((bookId, authorIds) -> {
            long v = Math.round(bookViews.getOrDefault(bookId, 0d));
            for (String a : authorIds) scores.merge(a, v, Long::sum);
        });
        for (List<String> authorIds : soldBookAuthors) for (String a : authorIds) scores.merge(a, SOLD_WEIGHT, Long::sum);
        for (List<String> authorIds : listedBookAuthors) for (String a : authorIds) scores.merge(a, LISTED_WEIGHT, Long::sum);
        return scores;
    }

    /** Only authors in {@code available} (the qualifying ones), by score then available count. */
    static List<ScoredAuthor> rank(Map<String, Long> scores, Map<String, Long> available) {
        return scores.entrySet().stream()
                .filter(e -> available.containsKey(e.getKey()))
                .sorted(Comparator.<Map.Entry<String, Long>>comparingLong(Map.Entry::getValue).reversed()
                        .thenComparing(e -> available.get(e.getKey()), Comparator.reverseOrder())
                        .thenComparing(Map.Entry::getKey))
                .map(e -> new ScoredAuthor(e.getKey(), e.getValue()))
                .toList();
    }

    private Iterable<Document> books(Bson filter) {
        return mongoTemplate.getCollection(mongoTemplate.getCollectionName(Book.class))
                .find(filter).projection(include("authors.authorId"));
    }

    private static List<String> authorIdsOf(Document book) {
        List<String> ids = new ArrayList<>();
        for (Document a : book.getList("authors", Document.class, List.of())) {
            Object id = a.get("authorId");
            if (id != null) ids.add(id.toString());
        }
        return ids;
    }

    private Map<String, Author> load(List<String> ids) {
        Map<String, Author> byId = new HashMap<>();
        if (ids.isEmpty()) return byId;
        for (Author a : mongoTemplate.find(Query.query(where("_id").in(ids)), Author.class)) byId.put(a.getId(), a);
        return byId;
    }
}

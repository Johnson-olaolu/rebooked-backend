package com.personal.rebooked.author.migration;

import com.mongodb.client.MongoCollection;
import com.mongodb.client.model.Filters;
import com.mongodb.client.model.Projections;
import com.mongodb.client.model.Updates;
import com.personal.rebooked.author.AuthorNames;
import com.personal.rebooked.author.AuthorReviewQueue;
import com.personal.rebooked.author.AuthorService;
import com.personal.rebooked.author.AuthorStats;
import com.personal.rebooked.author.models.Author;
import com.personal.rebooked.author.models.AuthorReviewItem;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.bson.BsonType;
import org.bson.Document;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import static org.springframework.data.mongodb.core.query.Criteria.where;
import static org.springframework.data.mongodb.core.query.Query.query;

/**
 * Turns the old free-text book authors into catalogue authors (§23e), and later flags migrated or proposed authors
 * that share a name with imported ones. Nothing is ever merged automatically on a name match.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AuthorMigration {
    private static final Pattern SEPARATORS = Pattern.compile(" & |;");
    private static final Pattern NEEDS_CHECK = Pattern.compile("(?i)\\band\\b|,");

    private final AuthorService authorService;
    private final AuthorReviewQueue reviewQueue;
    private final AuthorStats authorStats;
    private final MongoTemplate mongoTemplate;

    /** Names in the original order. needsCheck: contains "and" or a comma, which are never split on. */
    public record Split(List<String> names, boolean needsCheck) {}

    public static Split split(String raw) {
        if (raw == null) return new Split(List.of(), false);
        Map<String, String> names = new LinkedHashMap<>();
        for (String part : SEPARATORS.split(raw)) {
            String name = AuthorNames.clean(part);
            if (name == null || name.isEmpty()) continue;
            names.putIfAbsent(AuthorNames.normalize(name), name);
        }
        return new Split(new ArrayList<>(names.values()), NEEDS_CHECK.matcher(raw).find());
    }

    /**
     * Idempotent: only books with an author string and no authors field. Runs at startup after migrateBookSlugs.
     */
    public void migrateBookAuthors() {
        MongoCollection<Document> books = mongoTemplate.getCollection("book");
        var pending = Filters.and(Filters.type("author", BsonType.STRING), Filters.exists("authors", false));
        if (books.countDocuments(pending) == 0) return;

        Counts counts = new Counts();
        Map<String, Author> resolved = new HashMap<>();
        List<String> manualCheck = new ArrayList<>();

        for (Document book : books.find(pending).projection(Projections.include("author"))) {
            String raw = book.getString("author");
            Split split = split(raw);
            if (split.needsCheck()) manualCheck.add(raw);

            List<Document> entries = new ArrayList<>();
            for (String name : split.names()) {
                Author author = resolved.computeIfAbsent(AuthorNames.normalize(name), key -> resolve(name, counts));
                if (entries.stream().anyMatch(e -> author.getId().equals(e.getString("authorId")))) continue;
                entries.add(new Document("authorId", author.getId()).append("position", entries.size())
                        .append("name", author.getName()).append("slug", author.getSlug()));
            }
            books.updateOne(Filters.eq("_id", book.get("_id")), Updates.combine(
                    Updates.set("authors", entries), Updates.set("legacyAuthor", raw), Updates.unset("author")));
            // Per book, so a run interrupted by a restart leaves no author with a stale count
            authorStats.recount(entries.stream().map(e -> e.getString("authorId")).toList());
            counts.books++;
        }

        log.info("Author migration: {} books migrated; {} authors created, {} linked to existing, {} ambiguous",
                counts.books, counts.created, counts.linked, counts.ambiguous);
        if (!manualCheck.isEmpty()) {
            log.warn("Author migration: {} author strings contain \"and\" or a comma and were kept as one name; check them: {}",
                    manualCheck.size(), manualCheck);
        }
    }

    private Author resolve(String name, Counts counts) {
        List<Author> matches = authorService.sameName(name);
        if (matches.isEmpty()) {
            counts.created++;
            return authorService.create(name, Author.Origin.MIGRATED, Author.Status.UNVERIFIED, Author.Source.MIGRATION, null);
        }
        Author best = matches.get(0); // sameName is ranked: VERIFIED, then availableCount, then workCount
        if (matches.size() == 1) {
            counts.linked++;
        } else {
            counts.ambiguous++;
            for (Author other : matches.subList(1, matches.size())) {
                reviewQueue.addPair(AuthorReviewItem.Type.MIGRATION_AMBIGUOUS, best.getId(), other.getId(),
                        "Book author \"" + name + "\" matched several authors; linked to " + best.getName());
            }
        }
        return best;
    }

    private static final class Counts {
        int books, created, linked, ambiguous;
    }

    /**
     * After an Open Library import: MIGRATED and USER_PROPOSED authors whose name or alias exactly equals an imported
     * author's get a POSSIBLE_DUPLICATE item. Returns how many items were added.
     */
    public int findPossibleDuplicates() {
        Query local = query(where("origin").in(Author.Origin.MIGRATED, Author.Origin.USER_PROPOSED)
                .and("status").ne(Author.Status.MERGED));
        int added = 0;
        try (Stream<Author> authors = mongoTemplate.stream(local, Author.class)) {
            for (Author author : (Iterable<Author>) authors::iterator) {
                Set<String> names = new LinkedHashSet<>();
                if (author.getNormalizedName() != null) names.add(author.getNormalizedName());
                author.aliasList().forEach(a -> { if (a.getNormalizedName() != null) names.add(a.getNormalizedName()); });
                if (names.isEmpty()) continue;
                Query imported = query(new Criteria().orOperator(where("normalizedName").in(names),
                        where("aliases.normalizedName").in(names))
                        .and("origin").is(Author.Origin.IMPORTED).and("status").ne(Author.Status.MERGED)).limit(50);
                for (Author match : mongoTemplate.find(imported, Author.class)) {
                    if (reviewQueue.addPair(AuthorReviewItem.Type.POSSIBLE_DUPLICATE, author.getId(), match.getId(),
                            "Same name as an imported author")) {
                        added++;
                    }
                }
            }
        }
        if (added > 0) log.info("Duplicate pass: {} possible duplicates queued for review", added);
        return added;
    }
}

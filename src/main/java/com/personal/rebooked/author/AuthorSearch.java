package com.personal.rebooked.author;

import com.personal.rebooked.author.models.Author;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.stereotype.Component;

import java.util.*;

import static org.springframework.data.mongodb.core.query.Criteria.where;

/**
 * Author lookup by name or alias (§23c). Two indexed $elemMatch range scans on searchKeys, no regex, so Mongo uses index
 * bounds: authors with books on Rebooked (the partial index), then the wider catalogue. Ranking happens in memory.
 */
@Component
@RequiredArgsConstructor
public class AuthorSearch {
    static final int WITH_BOOKS_CAP = 300;
    static final int CATALOGUE_CAP = 300;

    /** Only what ranking and AuthorSummary need. */
    private static final String[] FIELDS = {"name", "slug", "aliases", "status", "availableCount", "workCount",
            "birthYear", "deathYear", "description"};

    private final MongoTemplate mongoTemplate;

    /** A ranked match. tier 0: exact name or alias; 1: the name starts with q; 2: a later word or an alias does. */
    public record Hit(Author author, int tier, String matchedAlias) {}

    /** Every match for an already-folded query (at least one character), best first. Never MERGED authors. */
    public List<Hit> search(String foldedQuery) {
        if (foldedQuery == null || foldedQuery.isEmpty()) return List.of();
        Map<String, Author> candidates = new LinkedHashMap<>();

        Query withBooks = new Query(keyPrefix(foldedQuery).and("availableCount").gt(0).and("status").ne(Author.Status.MERGED))
                .with(Sort.by(Sort.Direction.DESC, "availableCount"))
                .limit(WITH_BOOKS_CAP);
        withBooks.fields().include(FIELDS);
        for (Author a : mongoTemplate.find(withBooks, Author.class)) candidates.put(a.getId(), a);

        Query catalogue = new Query(keyPrefix(foldedQuery).and("status").ne(Author.Status.MERGED)).limit(CATALOGUE_CAP);
        catalogue.fields().include(FIELDS);
        for (Author a : mongoTemplate.find(catalogue, Author.class)) candidates.putIfAbsent(a.getId(), a);

        return rank(candidates.values(), foldedQuery);
    }

    /**
     * Authors with a searchKey that starts with q. On an array field a plain gte/lt applies each bound to any
     * element, so one key could satisfy the lower bound and another the upper. $elemMatch makes a single key
     * satisfy both (§23j), and still uses the multikey index with tight bounds.
     */
    public static Criteria keyPrefix(String foldedQuery) {
        return where("searchKeys").elemMatch(new Criteria().gte(foldedQuery).lt(foldedQuery + "￿"));
    }

    static List<Hit> rank(Collection<Author> authors, String q) {
        List<Hit> hits = new ArrayList<>();
        for (Author author : authors) hits.add(classify(author, q));
        hits.sort(ORDER);
        return hits;
    }

    static Hit classify(Author author, String q) {
        String name = AuthorNames.fold(author.getName());
        if (name.equals(q)) return new Hit(author, 0, null);
        List<String> aliases = author.aliasNames();
        for (String alias : aliases) {
            if (AuthorNames.fold(alias).equals(q)) return new Hit(author, 0, alias);
        }
        if (name.startsWith(q)) return new Hit(author, 1, null);
        if (AuthorNames.laterWordStartsWith(name, q)) return new Hit(author, 2, null);
        for (String alias : aliases) {
            String folded = AuthorNames.fold(alias);
            if (folded.startsWith(q) || AuthorNames.laterWordStartsWith(folded, q)) return new Hit(author, 2, alias);
        }
        // Only reachable when the matching word-start key was trimmed by the 40-key cap: keep it, last
        return new Hit(author, 3, null);
    }

    static final Comparator<Hit> ORDER = Comparator
            .comparingInt(Hit::tier)
            .thenComparing(h -> h.author().getAvailableCount(), Comparator.reverseOrder())
            .thenComparing(h -> !h.author().verified())
            .thenComparing(h -> h.author().getWorkCount(), Comparator.reverseOrder())
            .thenComparing(h -> h.author().getName() == null ? "" : h.author().getName(), String.CASE_INSENSITIVE_ORDER);
}

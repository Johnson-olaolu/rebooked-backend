package com.personal.rebooked.homepage.sections;

import com.fasterxml.jackson.core.type.TypeReference;
import com.personal.rebooked.author.models.Author;
import com.personal.rebooked.book.models.Book;
import com.personal.rebooked.homepage.HomepageCache;
import com.personal.rebooked.homepage.dto.HomepageStats;
import com.personal.rebooked.rating.SellerRating;
import com.personal.rebooked.utils.Constants;
import lombok.RequiredArgsConstructor;
import org.bson.Document;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.List;

import static org.springframework.data.mongodb.core.query.Criteria.where;

/** §24c stats, rebuilt every 15 minutes. */
@Component
@RequiredArgsConstructor
public class StatsSection {
    public static final String KEY = "homepage:stats";
    static final Duration TTL = Duration.ofMinutes(30);
    static final int MIN_RATINGS_FOR_AVERAGE = 10;

    private final HomepageCache cache;
    private final MongoTemplate mongoTemplate;

    public HomepageStats read() {
        return cache.get(KEY, new TypeReference<HomepageStats>() {}).orElseGet(this::build);
    }

    public HomepageStats build() {
        HomepageStats stats = compute();
        cache.put(KEY, stats, TTL);
        return stats;
    }

    HomepageStats compute() {
        long active = mongoTemplate.count(Query.query(where("status").is(Constants.BookStatus.ACTIVE)), Book.class);
        long sold = mongoTemplate.count(Query.query(where("status").is(Constants.BookStatus.SOLD)), Book.class);
        long readers = mongoTemplate.getCollection("user").countDocuments(
                new Document("isEmailVerified", true).append("isDeleted", new Document("$ne", true)));
        long sellers = countSellers();
        long authors = mongoTemplate.count(Query.query(where("status").ne(Author.Status.MERGED)
                .orOperator(where("availableCount").gt(0), where("soldCount").gt(0))), Author.class);
        return new HomepageStats(active, sold, readers, sellers, authors, averageRating());
    }

    /** Distinct owners of ACTIVE or SOLD books. Grouping on the whole DBRef avoids the "$user.$id" path. */
    private long countSellers() {
        List<Document> pipeline = List.of(
                new Document("$match", new Document("status", new Document("$in",
                        List.of(Constants.BookStatus.ACTIVE.name(), Constants.BookStatus.SOLD.name())))),
                new Document("$group", new Document("_id", "$user")),
                new Document("$count", "n"));
        Document row = mongoTemplate.getCollection(mongoTemplate.getCollectionName(Book.class)).aggregate(pipeline).first();
        return row == null ? 0 : ((Number) row.get("n")).longValue();
    }

    private Double averageRating() {
        List<Document> pipeline = List.of(new Document("$group", new Document("_id", null)
                .append("avg", new Document("$avg", "$stars")).append("n", new Document("$sum", 1))));
        Document row = mongoTemplate.getCollection(mongoTemplate.getCollectionName(SellerRating.class))
                .aggregate(pipeline).first();
        if (row == null || ((Number) row.get("n")).longValue() < MIN_RATINGS_FOR_AVERAGE) return null;
        return Math.round(((Number) row.get("avg")).doubleValue() * 10) / 10.0;
    }
}

package com.personal.rebooked.homepage.sections;

import com.fasterxml.jackson.core.type.TypeReference;
import com.mongodb.DBRef;
import com.personal.rebooked.book.models.Book;
import com.personal.rebooked.homepage.HomepageCache;
import com.personal.rebooked.user.dto.PublicUserDTO;
import com.personal.rebooked.user.models.Profile;
import com.personal.rebooked.user.models.SellerRatingSummary;
import com.personal.rebooked.user.models.User;
import com.personal.rebooked.user.repositories.UserRepository;
import com.personal.rebooked.utils.Constants;
import lombok.RequiredArgsConstructor;
import org.bson.Document;
import org.bson.types.ObjectId;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.*;

import static org.springframework.data.mongodb.core.query.Criteria.where;

/**
 * §24g trusted sellers: at least 3 ratings and an ACTIVE book, by average stars, then rating count, then books sold.
 * Daily. Each is the public seller summary with its rating, plus availableCount and soldCount.
 */
@Component
@RequiredArgsConstructor
public class SellersSection {
    public static final String KEY = "homepage:sellers";
    static final Duration TTL = Duration.ofHours(24);
    static final int COUNT = 4;
    static final int MIN_RATINGS = 3;

    private final HomepageCache cache;
    private final MongoTemplate mongoTemplate;
    private final UserRepository userRepository;

    record Candidate(String userId, double average, int count, long sold) {}

    public List<PublicUserDTO> read() {
        List<String> ids = cache.get(KEY, new TypeReference<List<String>>() {}).orElseGet(this::build);
        if (ids.isEmpty()) return List.of();
        Map<String, User> users = new HashMap<>();
        for (User u : userRepository.findAllById(ids)) if (!u.isDeleted()) users.put(u.getId(), u);
        Map<String, long[]> counts = bookCounts(ids);
        List<PublicUserDTO> sellers = new ArrayList<>();
        for (String id : ids) {
            User u = users.get(id);
            long[] c = counts.getOrDefault(id, new long[2]);
            if (u == null || c[0] == 0) continue; // nothing on the shelf any more
            sellers.add(PublicUserDTO.withSales(u, c[0], c[1]));
        }
        return sellers;
    }

    public List<String> build() {
        Map<ObjectId, SellerRatingSummary> byProfile = new HashMap<>();
        Query rated = Query.query(where("sellerRating.count").gte(MIN_RATINGS));
        rated.fields().include("sellerRating");
        for (Profile p : mongoTemplate.find(rated, Profile.class)) {
            if (p.getId() != null && ObjectId.isValid(p.getId())) byProfile.put(new ObjectId(p.getId()), p.getSellerRating());
        }
        List<String> ids = List.of();
        if (!byProfile.isEmpty()) {
            List<User> users = mongoTemplate.find(Query.query(where("profile.$id").in(byProfile.keySet())
                    .and("isDeleted").ne(true)), User.class);
            Map<String, long[]> counts = bookCounts(users.stream().map(User::getId).toList());
            List<Candidate> candidates = new ArrayList<>();
            for (User u : users) {
                if (u.getProfile() == null || u.getProfile().getId() == null) continue;
                SellerRatingSummary rating = byProfile.get(new ObjectId(u.getProfile().getId()));
                long[] c = counts.getOrDefault(u.getId(), new long[2]);
                if (rating == null || c[0] == 0) continue;
                candidates.add(new Candidate(u.getId(), rating.getAverage(), rating.getCount(), c[1]));
            }
            ids = rank(candidates).stream().limit(COUNT).map(Candidate::userId).toList();
        }
        cache.put(KEY, ids, TTL);
        return ids;
    }

    static List<Candidate> rank(List<Candidate> candidates) {
        return candidates.stream()
                .filter(c -> c.count() >= MIN_RATINGS)
                .sorted(Comparator.comparingDouble(Candidate::average).reversed()
                        .thenComparing(Comparator.comparingInt(Candidate::count).reversed())
                        .thenComparing(Comparator.comparingLong(Candidate::sold).reversed())
                        .thenComparing(Candidate::userId))
                .toList();
    }

    /** userId → {ACTIVE books, SOLD books}, in one aggregation. */
    private Map<String, long[]> bookCounts(List<String> userIds) {
        Map<String, long[]> counts = new HashMap<>();
        List<ObjectId> oids = userIds.stream().filter(ObjectId::isValid).map(ObjectId::new).toList();
        if (oids.isEmpty()) return counts;
        List<Document> pipeline = List.of(
                new Document("$match", new Document("user.$id", new Document("$in", oids))
                        .append("status", new Document("$in", List.of(Constants.BookStatus.ACTIVE.name(), Constants.BookStatus.SOLD.name())))),
                new Document("$group", new Document("_id", "$user")
                        .append("active", sumWhere(Constants.BookStatus.ACTIVE))
                        .append("sold", sumWhere(Constants.BookStatus.SOLD))));
        for (Document row : mongoTemplate.getCollection(mongoTemplate.getCollectionName(Book.class)).aggregate(pipeline)) {
            if (row.get("_id") instanceof DBRef ref) {
                counts.put(ref.getId().toString(), new long[]{((Number) row.get("active")).longValue(),
                        ((Number) row.get("sold")).longValue()});
            }
        }
        return counts;
    }

    private static Document sumWhere(Constants.BookStatus status) {
        return new Document("$sum", new Document("$cond", List.of(new Document("$eq", List.of("$status", status.name())), 1, 0)));
    }
}

package com.personal.rebooked.seed.demo;

import com.mongodb.client.MongoCollection;
import com.personal.rebooked.author.AuthorStats;
import com.personal.rebooked.author.models.Author;
import com.personal.rebooked.author.models.AuthorExternalIdentifier;
import com.personal.rebooked.book.models.Book;
import com.personal.rebooked.book.saved.SavedBook;
import com.personal.rebooked.book.views.BookViews;
import com.personal.rebooked.book.views.RecentViews;
import com.personal.rebooked.file.models.File;
import com.personal.rebooked.homepage.HomepageService;
import com.personal.rebooked.homepage.WeekViews;
import com.personal.rebooked.rating.SellerRating;
import com.personal.rebooked.user.models.Profile;
import com.personal.rebooked.user.models.User;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.bson.Document;
import org.bson.conversions.Bson;
import org.bson.types.ObjectId;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.*;

import static com.mongodb.client.model.Filters.*;
import static com.mongodb.client.model.Projections.include;

/**
 * --seed.demo.reset=true: removes what {@link DemoSeeder} created, plus anything made while testing with the demo
 * accounts (their conversations, notifications, ratings). Data belonging to other users is left alone; authors the
 * seeder created stay only while a non-demo book still uses them.
 */
@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnExpression("${seed.demo:false} or ${seed.demo.reset:false}")
public class DemoReset {
    private static final int CHUNK = 1000;
    private static final int DAY_KEYS = 9;

    private final MongoTemplate mongoTemplate;
    private final StringRedisTemplate redis;
    private final AuthorStats authorStats;
    private final WeekViews weekViews;
    private final HomepageService homepageService;

    public void reset() {
        long started = System.currentTimeMillis();
        List<ObjectId> userIds = new ArrayList<>();
        List<ObjectId> profileIds = new ArrayList<>();
        for (Document u : collection(User.class).find(regex("email", DemoSeeder.EMAIL_REGEX)).projection(include("profile"))) {
            userIds.add(u.getObjectId("_id"));
            Object profile = u.get("profile");
            if (profile instanceof com.mongodb.DBRef ref && ref.getId() instanceof ObjectId id) profileIds.add(id);
        }
        List<String> userIdStrings = userIds.stream().map(ObjectId::toHexString).toList();

        List<ObjectId> bookIds = new ArrayList<>();
        Set<String> touchedAuthors = new HashSet<>();
        if (!userIds.isEmpty()) {
            for (Document b : collection(Book.class).find(in("user.$id", userIds)).projection(include("authors.authorId"))) {
                bookIds.add(b.getObjectId("_id"));
                for (Document a : b.getList("authors", Document.class, List.of())) {
                    if (a.get("authorId") != null) touchedAuthors.add(a.get("authorId").toString());
                }
            }
        }
        List<String> bookIdStrings = bookIds.stream().map(ObjectId::toHexString).toList();

        Map<String, Long> removed = new LinkedHashMap<>();
        if (!userIds.isEmpty()) {
            // Non-demo sellers rated by demo buyers get their summary recomputed afterwards
            Set<String> ratedSellers = new HashSet<>(collection(SellerRating.class)
                    .distinct("sellerId", and(in("buyerId", userIdStrings), nin("sellerId", userIdStrings)), String.class)
                    .into(new ArrayList<>()));

            removed.put("books", collection(Book.class).deleteMany(in("user.$id", userIds)).getDeletedCount());
            removed.put("files", collection(File.class).deleteMany(in("user.$id", userIds)).getDeletedCount());
            removed.put("saved books", deleteIn(SavedBook.class, "userId", userIdStrings)
                    + deleteIn(SavedBook.class, "bookId", bookIdStrings));
            removed.put("seller ratings", deleteIn(SellerRating.class, "sellerId", userIdStrings)
                    + deleteIn(SellerRating.class, "buyerId", userIdStrings));
            // Its _id is the user id, stored as an ObjectId
            removed.put("recently viewed", collection(RecentViews.class).deleteMany(in("_id", userIds)).getDeletedCount());
            for (int from = 0; from < bookIdStrings.size(); from += CHUNK) {
                collection(RecentViews.class).updateMany(new Document(), new Document("$pull", new Document("items",
                        new Document("bookId", new Document("$in", slice(bookIdStrings, from))))));
            }

            List<String> conversationIds = mongoTemplate.getCollection("conversations")
                    .find(in("participantIds", userIdStrings)).projection(include("_id"))
                    .map(d -> d.getObjectId("_id").toHexString()).into(new ArrayList<>());
            removed.put("messages", deleteIn("messages", "conversationId", conversationIds));
            removed.put("conversations", deleteIn("conversations", "participantIds", userIdStrings));
            removed.put("notifications", deleteIn("notifications", "userId", userIdStrings)
                    + deleteIn("notification_deliveries", "userId", userIdStrings)
                    + deleteIn("notification_preferences", "userId", userIdStrings));
            removed.put("profiles", collection(Profile.class).deleteMany(in("_id", profileIds)).getDeletedCount());
            removed.put("users", collection(User.class).deleteMany(in("_id", userIds)).getDeletedCount());

            for (String sellerId : ratedSellers) recomputeRating(sellerId);
        }

        // Authors: recount the ones demo books used, then drop seeded authors no remaining book refers to
        List<String> seededAuthors = collection(Author.class).find(eq("createdBy", DemoSeeder.CREATED_BY))
                .projection(include("_id")).map(d -> d.getObjectId("_id").toHexString()).into(new ArrayList<>());
        Set<String> stillUsed = new HashSet<>();
        for (int from = 0; from < seededAuthors.size(); from += CHUNK) {
            collection(Book.class).distinct("authors.authorId", in("authors.authorId", slice(seededAuthors, from)), String.class)
                    .into(stillUsed);
        }
        List<String> orphaned = seededAuthors.stream().filter(id -> !stillUsed.contains(id)).toList();
        long authors = 0;
        for (int from = 0; from < orphaned.size(); from += CHUNK) {
            List<ObjectId> ids = slice(orphaned, from).stream().map(ObjectId::new).toList();
            authors += collection(Author.class).deleteMany(in("_id", ids)).getDeletedCount();
        }
        deleteIn(AuthorExternalIdentifier.class, "authorId", orphaned);
        removed.put("authors", authors);
        touchedAuthors.removeAll(orphaned);
        authorStats.recount(touchedAuthors);

        // Redis: demo books out of the day buckets and pending counts, demo users' recents gone
        LocalDate today = LocalDate.now(BookViews.ZONE);
        for (int from = 0; from < bookIdStrings.size(); from += CHUNK) {
            Object[] members = slice(bookIdStrings, from).toArray();
            for (int d = 0; d < DAY_KEYS; d++) redis.opsForZSet().remove(BookViews.dayKey(today.minusDays(d)), members);
            redis.opsForHash().delete(BookViews.PENDING, members);
        }
        if (!userIdStrings.isEmpty()) {
            redis.delete(userIdStrings.stream().map(BookViews::recentKey).toList());
            redis.opsForSet().remove(BookViews.DIRTY, userIdStrings.toArray());
        }
        weekViews.rebuild();
        Map<String, Object> homepage = homepageService.refresh("all");

        if (userIds.isEmpty() && authors == 0) {
            log.info("Demo reset: there was no demo data");
        } else {
            log.info("Demo reset in {} s, removed {}; homepage refresh {}", (System.currentTimeMillis() - started) / 1000,
                    removed, homepage);
        }
    }

    /** Profile.sellerRating from the seller's remaining ratings, or removed when none are left. */
    private void recomputeRating(String sellerId) {
        if (!ObjectId.isValid(sellerId)) return;
        Document seller = collection(User.class).find(eq("_id", new ObjectId(sellerId))).projection(include("profile")).first();
        if (seller == null || !(seller.get("profile") instanceof com.mongodb.DBRef ref)) return;
        List<Integer> stars = collection(SellerRating.class).find(eq("sellerId", sellerId)).projection(include("stars"))
                .map(d -> d.getInteger("stars", 0)).into(new ArrayList<>());
        Document update = stars.isEmpty()
                ? new Document("$unset", new Document("sellerRating", ""))
                : new Document("$set", new Document("sellerRating", DemoSeeder.ratingSummary(stars)));
        collection(Profile.class).updateOne(eq("_id", ref.getId()), update);
    }

    private long deleteIn(Class<?> type, String field, List<String> values) {
        return deleteIn(mongoTemplate.getCollectionName(type), field, values);
    }

    private long deleteIn(String collection, String field, List<String> values) {
        long deleted = 0;
        for (int from = 0; from < values.size(); from += CHUNK) {
            Bson filter = in(field, slice(values, from));
            deleted += mongoTemplate.getCollection(collection).deleteMany(filter).getDeletedCount();
        }
        return deleted;
    }

    private MongoCollection<Document> collection(Class<?> type) {
        return mongoTemplate.getCollection(mongoTemplate.getCollectionName(type));
    }

    private static List<String> slice(List<String> values, int from) {
        return values.subList(from, Math.min(from + CHUNK, values.size()));
    }
}

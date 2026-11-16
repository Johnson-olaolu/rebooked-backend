package com.personal.rebooked.book.views;

import com.mongodb.DBRef;
import com.personal.rebooked.book.models.Book;
import com.personal.rebooked.utils.Constants;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.bson.Document;
import org.bson.types.ObjectId;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ZSetOperations;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.time.Duration;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.*;

import static com.mongodb.client.model.Filters.eq;
import static com.mongodb.client.model.Projections.include;

/**
 * Book views and recently viewed, kept in Redis so a view never writes to Mongo.
 * {@link ViewFlushJob} moves the counts and recents to Mongo in batches.
 *
 * <ul>
 *   <li>{@code viewed:{viewer}:{yyyyMMdd}} SET of bookIds: daily dedupe, 26 h</li>
 *   <li>{@code views:pending} HASH bookId → n: counts not yet in Mongo, no expiry</li>
 *   <li>{@code recent:{userId}} ZSET bookId → viewedAt ms: newest 20, 30 days</li>
 *   <li>{@code recent:dirty} SET of userIds whose recents changed since the last flush, no expiry</li>
 * </ul>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class BookViews {

    public static final String PENDING = "views:pending";
    public static final String FLUSHING = "views:flushing";
    public static final String DIRTY = "recent:dirty";
    public static final int RECENT_MAX = 20;
    static final Duration DEDUPE_TTL = Duration.ofHours(26);
    static final Duration RECENT_TTL = Duration.ofDays(30);
    private static final ZoneId ZONE = ZoneId.of("Africa/Lagos");
    private static final DateTimeFormatter DAY = DateTimeFormatter.BASIC_ISO_DATE;

    /**
     * KEYS: dedupe set, pending hash, recents zset, dirty set.
     * ARGV: bookId, now ms, dedupe ttl s, recents ttl s, userId ("" when logged out).
     * Returns {counted (0/1), recents existed before this view (0/1)}.
     */
    private static final DefaultRedisScript<List> RECORD = new DefaultRedisScript<>("""
            local counted = redis.call('SADD', KEYS[1], ARGV[1])
            if counted == 1 then
              if redis.call('TTL', KEYS[1]) < 0 then redis.call('EXPIRE', KEYS[1], ARGV[3]) end
              redis.call('HINCRBY', KEYS[2], ARGV[1], 1)
            end
            local existed = 1
            if ARGV[5] ~= '' then
              existed = redis.call('EXISTS', KEYS[3])
              redis.call('ZADD', KEYS[3], ARGV[2], ARGV[1])
              redis.call('ZREMRANGEBYRANK', KEYS[3], 0, -(tonumber(ARGV[6]) + 1))
              redis.call('EXPIRE', KEYS[3], ARGV[4])
              redis.call('SADD', KEYS[4], ARGV[5])
            end
            return {counted, existed}
            """, List.class);

    private final StringRedisTemplate redis;
    private final MongoTemplate mongoTemplate;

    public static String recentKey(String userId) {
        return "recent:" + userId;
    }

    /**
     * PATCH /book/{id}/view. 404 unless the book is ACTIVE. The owner's own views are ignored.
     * Redis trouble is logged and swallowed: viewing must never break the book page.
     */
    public void record(String bookId, String userId, String ip) {
        String ownerId = activeOwnerOf(bookId);
        if (userId != null && userId.equals(ownerId)) return;

        String viewer = userId != null ? "user:" + userId : "ip:" + ip;
        String day = LocalDate.now(ZONE).format(DAY);
        try {
            List<?> result = redis.execute(RECORD,
                    List.of("viewed:" + viewer + ":" + day, PENDING,
                            recentKey(userId == null ? "-" : userId), DIRTY),
                    bookId, String.valueOf(System.currentTimeMillis()),
                    String.valueOf(DEDUPE_TTL.toSeconds()), String.valueOf(RECENT_TTL.toSeconds()),
                    userId == null ? "" : userId, String.valueOf(RECENT_MAX));
            boolean recentsExisted = result == null || result.size() < 2 || toLong(result.get(1)) == 1;
            if (userId != null && !recentsExisted) {
                // The list had expired or been evicted: merge the Mongo copy back under this view
                restoreRecents(userId);
            }
        } catch (Exception e) {
            log.warn("Could not record a view of book {}: {}", bookId, e.toString());
        }
    }

    /** Newest first. Reloads the Redis list from Mongo when it's missing. */
    public List<String> recentIds(String userId) {
        String key = recentKey(userId);
        try {
            if (!Boolean.TRUE.equals(redis.hasKey(key))) restoreRecents(userId);
            Set<String> ids = redis.opsForZSet().reverseRange(key, 0, RECENT_MAX - 1);
            return ids == null ? List.of() : new ArrayList<>(ids);
        } catch (Exception e) {
            log.warn("Recently viewed for {} read from Mongo: {}", userId, e.toString());
            RecentViews saved = mongoTemplate.findById(userId, RecentViews.class);
            if (saved == null || saved.getItems() == null) return List.of();
            return saved.getItems().stream().map(RecentViews.Item::bookId).toList();
        }
    }

    /** DELETE /book/recently-viewed */
    public void clearRecents(String userId) {
        redis.delete(recentKey(userId));
        redis.opsForSet().remove(DIRTY, userId);
        mongoTemplate.remove(org.springframework.data.mongodb.core.query.Query.query(
                org.springframework.data.mongodb.core.query.Criteria.where("_id").is(userId)), RecentViews.class);
    }

    /** Adds the Mongo copy to the Redis list. Entries already there win (ZADD NX), so a view just recorded keeps its time. */
    private void restoreRecents(String userId) {
        RecentViews saved = mongoTemplate.findById(userId, RecentViews.class);
        if (saved == null || saved.getItems() == null || saved.getItems().isEmpty()) return;
        Set<ZSetOperations.TypedTuple<String>> tuples = new HashSet<>();
        for (RecentViews.Item item : saved.getItems()) {
            tuples.add(ZSetOperations.TypedTuple.of(item.bookId(), (double) item.viewedAt()));
        }
        String key = recentKey(userId);
        redis.opsForZSet().addIfAbsent(key, tuples);
        redis.opsForZSet().removeRange(key, 0, -(RECENT_MAX + 1));
        redis.expire(key, RECENT_TTL);
    }

    /** The owner's id of an ACTIVE book, from one small projected read (no DBRef loading). 404 otherwise. */
    private String activeOwnerOf(String bookId) {
        if (bookId == null || !ObjectId.isValid(bookId)) throw notFound();
        Document doc = mongoTemplate.getCollection(mongoTemplate.getCollectionName(Book.class))
                .find(eq("_id", new ObjectId(bookId)))
                .projection(include("status", "user"))
                .first();
        if (doc == null || !Constants.BookStatus.ACTIVE.name().equals(doc.getString("status"))) throw notFound();
        Object user = doc.get("user");
        return user instanceof DBRef ref ? ref.getId().toString() : null;
    }

    private static ResponseStatusException notFound() {
        return new ResponseStatusException(HttpStatus.NOT_FOUND, "Book not found");
    }

    static long toLong(Object value) {
        return value instanceof Number n ? n.longValue() : Long.parseLong(value.toString());
    }
}

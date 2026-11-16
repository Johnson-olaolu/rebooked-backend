package com.personal.rebooked.book.views;

import com.mongodb.DBRef;
import com.personal.rebooked.book.models.Book;
import com.personal.rebooked.book.repositories.BookRepository;
import com.personal.rebooked.notification.BookSnapshot;
import com.personal.rebooked.notification.NotificationService;
import com.personal.rebooked.notification.Notifications;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.bson.Document;
import org.bson.types.ObjectId;
import org.springframework.context.SmartLifecycle;
import org.springframework.data.mongodb.core.BulkOperations;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ZSetOperations;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.*;

import static com.mongodb.client.model.Filters.in;
import static com.mongodb.client.model.Projections.include;

/**
 * Moves view counts and recently viewed lists from Redis to Mongo in batches
 * (every {@code views.flush-interval}, default 5 minutes, and once on shutdown).
 * Mongo writes per run: at most one per distinct book viewed plus one per distinct user who viewed.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ViewFlushJob implements SmartLifecycle {

    static final List<Integer> VIEW_MILESTONES = List.of(50, 100, 500, 1000);
    private static final String LOCK = "lock:views-flush";
    private static final int CHUNK = 1000;
    private static final int DIRTY_BATCH = 500;

    private final StringRedisTemplate redis;
    private final MongoTemplate mongoTemplate;
    private final BookRepository bookRepository;
    private final NotificationService notificationService;
    private final Notifications notifications;
    private final String instanceId = UUID.randomUUID().toString();

    @Scheduled(fixedDelayString = "${views.flush-interval:PT5M}", initialDelayString = "${views.flush-interval:PT5M}")
    public void scheduledFlush() {
        flush();
    }

    // ---------------------------------------------------------------- shutdown flush
    // A lifecycle stop, not @PreDestroy: the Redis client stops in the lifecycle phase, before any
    // @PreDestroy runs. This phase stops after the web server (no new views) and before Redis.

    private volatile boolean running;

    @Override
    public void start() {
        running = true;
    }

    @Override
    public void stop() {
        running = false;
        flush();
    }

    @Override
    public boolean isRunning() {
        return running;
    }

    @Override
    public int getPhase() {
        return SmartLifecycle.DEFAULT_PHASE - 4096;
    }

    public synchronized void flush() {
        try {
            // One instance at a time, once there's more than one
            if (!Boolean.TRUE.equals(redis.opsForValue().setIfAbsent(LOCK, instanceId, Duration.ofSeconds(240)))) return;
        } catch (Exception e) {
            log.warn("View flush skipped, Redis unavailable: {}", e.toString());
            return;
        }
        try {
            flushCounts();
            flushRecents();
        } catch (Exception e) {
            log.error("View flush failed; it will be retried next run", e);
        } finally {
            try {
                if (instanceId.equals(redis.opsForValue().get(LOCK))) redis.delete(LOCK);
            } catch (Exception ignored) {
                // The lock expires on its own
            }
        }
    }

    // ---------------------------------------------------------------- counts

    private void flushCounts() {
        // A leftover views:flushing is from a run that crashed; finish it before taking new counts
        if (!Boolean.TRUE.equals(redis.hasKey(BookViews.FLUSHING))) {
            if (!Boolean.TRUE.equals(redis.hasKey(BookViews.PENDING))) return;
            redis.rename(BookViews.PENDING, BookViews.FLUSHING);
        }
        Map<Object, Object> raw = redis.opsForHash().entries(BookViews.FLUSHING);
        Map<String, Long> counts = new LinkedHashMap<>();
        raw.forEach((k, v) -> {
            String id = k.toString();
            if (ObjectId.isValid(id)) counts.put(id, BookViews.toLong(v));
        });

        List<String> ids = new ArrayList<>(counts.keySet());
        for (int from = 0; from < ids.size(); from += CHUNK) {
            List<String> chunk = ids.subList(from, Math.min(from + CHUNK, ids.size()));
            BulkOperations bulk = mongoTemplate.bulkOps(BulkOperations.BulkMode.UNORDERED, Book.class);
            for (String id : chunk) {
                bulk.updateOne(Query.query(Criteria.where("_id").is(new ObjectId(id))),
                        // An int, so viewCount stays int32 like the rest of the documents
                        new Update().inc("viewCount", (int) Math.min(counts.get(id), Integer.MAX_VALUE)));
            }
            bulk.execute();
            sendMilestones(chunk, counts);
        }
        redis.delete(BookViews.FLUSHING);
        if (!counts.isEmpty()) log.info("Flushed views for {} books", counts.size());
    }

    /** VIEWS_MILESTONE for every milestone each book crossed in this flush. */
    private void sendMilestones(List<String> ids, Map<String, Long> counts) {
        List<ObjectId> objectIds = ids.stream().map(ObjectId::new).toList();
        for (Document doc : mongoTemplate.getCollection(mongoTemplate.getCollectionName(Book.class))
                .find(in("_id", objectIds)).projection(include("viewCount", "user"))) {
            String id = doc.getObjectId("_id").toHexString();
            long after = doc.get("viewCount") instanceof Number n ? n.longValue() : 0;
            long before = after - counts.getOrDefault(id, 0L);
            List<Integer> crossed = VIEW_MILESTONES.stream().filter(m -> m > before && m <= after).toList();
            if (crossed.isEmpty() || !(doc.get("user") instanceof DBRef owner)) continue;

            // Rare, so the full book (for the title and cover) is only loaded here
            bookRepository.findById(id).ifPresent(book -> {
                for (int milestone : crossed) {
                    try {
                        notificationService.send(notifications.viewsMilestone(owner.getId().toString(),
                                BookSnapshot.of(book), milestone));
                    } catch (Exception e) {
                        log.warn("Views milestone for book {} failed: {}", id, e.toString());
                    }
                }
            });
        }
    }

    // ---------------------------------------------------------------- recents

    private void flushRecents() {
        int users = 0;
        while (true) {
            List<String> userIds = redis.opsForSet().pop(BookViews.DIRTY, DIRTY_BATCH);
            if (userIds == null || userIds.isEmpty()) break;
            Date now = new Date();
            BulkOperations bulk = mongoTemplate.bulkOps(BulkOperations.BulkMode.UNORDERED, RecentViews.class);
            int writes = 0;
            for (String userId : userIds) {
                Set<ZSetOperations.TypedTuple<String>> entries = redis.opsForZSet()
                        .reverseRangeWithScores(BookViews.recentKey(userId), 0, BookViews.RECENT_MAX - 1);
                if (entries == null || entries.isEmpty()) continue;
                List<RecentViews.Item> items = new ArrayList<>();
                for (ZSetOperations.TypedTuple<String> entry : entries) {
                    items.add(new RecentViews.Item(entry.getValue(),
                            entry.getScore() == null ? 0 : entry.getScore().longValue()));
                }
                bulk.upsert(Query.query(Criteria.where("_id").is(userId)),
                        new Update().set("items", items).set("updatedAt", now));
                writes++;
            }
            if (writes > 0) {
                try {
                    bulk.execute();
                } catch (RuntimeException e) {
                    // Put them back so the next run retries them
                    redis.opsForSet().add(BookViews.DIRTY, userIds.toArray(String[]::new));
                    throw e;
                }
            }
            users += writes;
        }
        if (users > 0) log.info("Flushed recently viewed for {} users", users);
    }
}

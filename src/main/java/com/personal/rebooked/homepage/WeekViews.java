package com.personal.rebooked.homepage;

import com.personal.rebooked.book.views.BookViews;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ZSetOperations;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Duration;
import java.time.LocalDate;
import java.util.*;

/**
 * Views over the last 7 days (§24a): {@code views:week} is the ZUNIONSTORE of the 7 newest
 * {@code views:day:{yyyyMMdd}} buckets, rebuilt hourly by the trending job and kept for 2 h.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class WeekViews {
    public static final String KEY = "views:week";
    static final Duration TTL = Duration.ofHours(2);
    /** How many of the week's most-viewed books the scoring jobs look at. */
    static final int SCORE_CAP = 5000;

    private final StringRedisTemplate redis;
    private final Clock clock;

    /** Rebuilds views:week from the day buckets. */
    public void rebuild() {
        LocalDate[] days = HomepageWeek.lastSevenDays(clock);
        String first = BookViews.dayKey(days[0]);
        List<String> others = new ArrayList<>();
        for (int i = 1; i < days.length; i++) others.add(BookViews.dayKey(days[i]));
        Long size = redis.opsForZSet().unionAndStore(first, others, KEY);
        if (size != null && size > 0) redis.expire(KEY, TTL);
    }

    /** bookId → views this week, most viewed first. Rebuilds the union when it's missing. Empty if Redis is down. */
    public LinkedHashMap<String, Double> scores() {
        LinkedHashMap<String, Double> scores = new LinkedHashMap<>();
        try {
            if (!Boolean.TRUE.equals(redis.hasKey(KEY))) rebuild();
            Set<ZSetOperations.TypedTuple<String>> rows = redis.opsForZSet().reverseRangeWithScores(KEY, 0, SCORE_CAP - 1);
            if (rows != null) {
                for (ZSetOperations.TypedTuple<String> row : rows) {
                    if (row.getValue() != null) scores.put(row.getValue(), row.getScore() == null ? 0 : row.getScore());
                }
            }
        } catch (Exception e) {
            log.warn("Week views unavailable: {}", e.toString());
        }
        return scores;
    }

    /** The most viewed book ids this week, from {@code start} (inclusive) for {@code count}. */
    public List<String> ranked(long start, long count) {
        try {
            if (!Boolean.TRUE.equals(redis.hasKey(KEY))) rebuild();
            Set<String> ids = redis.opsForZSet().reverseRange(KEY, start, start + count - 1);
            return ids == null ? List.of() : new ArrayList<>(ids);
        } catch (Exception e) {
            log.warn("Week views unavailable: {}", e.toString());
            return List.of();
        }
    }
}

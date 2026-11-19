package com.personal.rebooked.homepage;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.Optional;

/**
 * The homepage snapshots in Redis, stored as JSON strings. A missing key, a value that no longer parses, or Redis
 * being down all read as "missing", so the section rebuilds rather than failing the page.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class HomepageCache {
    private final StringRedisTemplate redis;
    private final ObjectMapper objectMapper;

    public <T> Optional<T> get(String key, TypeReference<T> type) {
        try {
            String json = redis.opsForValue().get(key);
            if (json == null) return Optional.empty();
            return Optional.ofNullable(objectMapper.readValue(json, type));
        } catch (Exception e) {
            log.warn("Homepage snapshot {} unreadable: {}", key, e.toString());
            return Optional.empty();
        }
    }

    public void put(String key, Object value, Duration ttl) {
        try {
            redis.opsForValue().set(key, objectMapper.writeValueAsString(value), ttl);
        } catch (Exception e) {
            log.warn("Homepage snapshot {} not saved: {}", key, e.toString());
        }
    }

    public boolean exists(String key) {
        try {
            return Boolean.TRUE.equals(redis.hasKey(key));
        } catch (Exception e) {
            return false;
        }
    }

    public void expire(String key, Duration ttl) {
        try {
            redis.expire(key, ttl);
        } catch (Exception e) {
            log.warn("Homepage snapshot {} TTL not extended: {}", key, e.toString());
        }
    }

    public void evict(String key) {
        try {
            redis.delete(key);
        } catch (Exception e) {
            log.warn("Homepage snapshot {} not evicted: {}", key, e.toString());
        }
    }

    /** Fixed-window counter; fails open (returns 0) when Redis is down. */
    public long increment(String key, Duration window) {
        try {
            Long value = redis.opsForValue().increment(key);
            if (value != null && value == 1L) redis.expire(key, window);
            return value == null ? 0 : value;
        } catch (Exception e) {
            return 0;
        }
    }

    /** SET NX with a TTL; true when this caller got it. Fails closed when Redis is down. */
    public boolean claim(String key, Duration ttl) {
        try {
            return Boolean.TRUE.equals(redis.opsForValue().setIfAbsent(key, "1", ttl));
        } catch (Exception e) {
            return false;
        }
    }
}

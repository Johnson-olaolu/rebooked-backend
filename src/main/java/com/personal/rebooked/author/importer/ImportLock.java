package com.personal.rebooked.author.importer;

import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.List;
import java.util.UUID;

/**
 * One author import at a time across instances: SET NX EX 3600, renewed while running, released only by its
 * owner (compare-and-expire / compare-and-delete in Lua, so another instance's lock is never touched).
 */
@Component
@RequiredArgsConstructor
public class ImportLock {
    static final String KEY = "lock:author-import";
    static final Duration TTL = Duration.ofHours(1);

    private static final DefaultRedisScript<Long> RENEW = new DefaultRedisScript<>(
            "if redis.call('GET', KEYS[1]) == ARGV[1] then return redis.call('EXPIRE', KEYS[1], ARGV[2]) else return 0 end",
            Long.class);
    private static final DefaultRedisScript<Long> RELEASE = new DefaultRedisScript<>(
            "if redis.call('GET', KEYS[1]) == ARGV[1] then return redis.call('DEL', KEYS[1]) else return 0 end",
            Long.class);

    private final StringRedisTemplate redis;
    private final String instanceId = UUID.randomUUID().toString();

    public boolean acquire() {
        return Boolean.TRUE.equals(redis.opsForValue().setIfAbsent(KEY, instanceId, TTL));
    }

    /** Extends the lock if this instance still holds it. Returns false if it was lost. */
    public boolean renew() {
        Long result = redis.execute(RENEW, List.of(KEY), instanceId, String.valueOf(TTL.toSeconds()));
        return result != null && result == 1L;
    }

    public void release() {
        redis.execute(RELEASE, List.of(KEY), instanceId);
    }

    public boolean held() {
        return Boolean.TRUE.equals(redis.hasKey(KEY));
    }
}

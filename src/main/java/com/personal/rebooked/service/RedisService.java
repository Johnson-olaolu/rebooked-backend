package com.personal.rebooked.service;

import com.google.gson.Gson;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.util.Set;
import java.util.concurrent.TimeUnit;

@RequiredArgsConstructor
@Service
public class RedisService {

    private final RedisTemplate<String, Object> redisTemplate;

    // Save value with optional expiration
    public void save(String key, Object value, long timeout, TimeUnit unit) {
        redisTemplate.opsForValue().set(key, value, timeout, unit);
    }

    // Save value without expiration
    public void save(String key, Object value) {
        redisTemplate.opsForValue().set(key, value);
    }

    // Retrieve value
    public Object get(String key) {
        return redisTemplate.opsForValue().get(key);
    }

    // Delete key
    public void delete(String key) {
        redisTemplate.delete(key);
    }

    // Check if key exists
    public boolean exists(String key) {
        return redisTemplate.hasKey(key);
    }

    // Add a member to a set and (re)set the set's expiry
    public void addToSet(String key, Object value, long timeout, TimeUnit unit) {
        redisTemplate.opsForSet().add(key, value);
        redisTemplate.expire(key, timeout, unit);
    }

    public Set<Object> members(String key) {
        Set<Object> members = redisTemplate.opsForSet().members(key);
        return members == null ? Set.of() : members;
    }

    public void removeFromSet(String key, Object value) {
        redisTemplate.opsForSet().remove(key, value);
    }

    // Increment a counter; the expiry starts with the first increment
    public long increment(String key, long timeout, TimeUnit unit) {
        Long value = redisTemplate.opsForValue().increment(key);
        if (value != null && value == 1L) {
            redisTemplate.expire(key, timeout, unit);
        }
        return value == null ? 0 : value;
    }
}

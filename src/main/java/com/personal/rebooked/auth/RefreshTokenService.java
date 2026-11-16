package com.personal.rebooked.auth;

import com.personal.rebooked.exception.ApiException;
import com.personal.rebooked.service.RedisService;
import com.personal.rebooked.user.UserService;
import com.personal.rebooked.user.models.User;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.security.SecureRandom;
import java.util.Base64;
import java.util.concurrent.TimeUnit;

/**
 * Opaque, single-use refresh tokens stored in Redis as refresh:&lt;token&gt; -> userId.
 * Each user's live tokens are also kept in the set refresh-user:&lt;userId&gt;, so they can all be revoked at once.
 */
@RequiredArgsConstructor
@Service
public class RefreshTokenService {

    private static final String KEY_PREFIX = "refresh:";
    private static final String USER_PREFIX = "refresh-user:";
    private static final SecureRandom RANDOM = new SecureRandom();

    private final RedisService redisService;
    private final UserService userService;

    @Value("${security.jwt.refresh-expiration-time}")
    private long refreshExpiration;

    public String issue(User user) {
        byte[] bytes = new byte[32];
        RANDOM.nextBytes(bytes);
        String token = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
        redisService.save(KEY_PREFIX + token, user.getId(), refreshExpiration, TimeUnit.MILLISECONDS);
        redisService.addToSet(USER_PREFIX + user.getId(), token, refreshExpiration, TimeUnit.MILLISECONDS);
        return token;
    }

    /** Consumes the refresh token and returns its user. The caller issues a new pair. */
    public User consume(String token) {
        String key = KEY_PREFIX + token;
        Object userId = redisService.get(key);
        if (userId == null) {
            throw new ApiException(HttpStatus.UNAUTHORIZED, "Invalid or expired refresh token");
        }
        redisService.delete(key);
        redisService.removeFromSet(USER_PREFIX + userId, token);
        User user = userService.findUserById(userId.toString());
        if (user.isDeleted()) {
            throw new ApiException(HttpStatus.UNAUTHORIZED, "Invalid or expired refresh token");
        }
        return user;
    }

    public void revoke(String token) {
        Object userId = redisService.get(KEY_PREFIX + token);
        redisService.delete(KEY_PREFIX + token);
        if (userId != null) {
            redisService.removeFromSet(USER_PREFIX + userId, token);
        }
    }

    /** Signs the user out everywhere: every refresh token they hold stops working. */
    public void revokeAll(String userId) {
        String setKey = USER_PREFIX + userId;
        for (Object token : redisService.members(setKey)) {
            redisService.delete(KEY_PREFIX + token);
        }
        redisService.delete(setKey);
    }
}

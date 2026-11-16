package com.personal.rebooked.notification;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Base64;

/** Signed one-click unsubscribe tokens: "<userId>|<type>|<channel>" plus an HMAC, with no expiry. */
@Component
public class UnsubscribeTokens {
    public record Parsed(String userId, NotificationType type, NotificationChannel channel) {}

    private static final Base64.Encoder ENCODER = Base64.getUrlEncoder().withoutPadding();
    private static final Base64.Decoder DECODER = Base64.getUrlDecoder();

    private final byte[] key;

    public UnsubscribeTokens(@Value("${security.jwt.secret-key}") String secret) {
        // Domain-separated from JWT signing, though derived from the same secret
        this.key = ("unsubscribe:" + secret).getBytes(StandardCharsets.UTF_8);
    }

    public String create(String userId, NotificationType type, NotificationChannel channel) {
        String payload = userId + "|" + type.name() + "|" + channel.name();
        return ENCODER.encodeToString(payload.getBytes(StandardCharsets.UTF_8)) + "." + ENCODER.encodeToString(sign(payload));
    }

    /** null when the token is malformed or the signature doesn't match. */
    public Parsed parse(String token) {
        if (token == null) return null;
        try {
            String[] parts = token.trim().split("\\.");
            if (parts.length != 2) return null;
            String payload = new String(DECODER.decode(parts[0]), StandardCharsets.UTF_8);
            if (!MessageDigest.isEqual(sign(payload), DECODER.decode(parts[1]))) return null;
            String[] fields = payload.split("\\|");
            if (fields.length != 3) return null;
            return new Parsed(fields[0], NotificationType.valueOf(fields[1]), NotificationChannel.valueOf(fields[2]));
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    private byte[] sign(String payload) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(key, "HmacSHA256"));
            return mac.doFinal(payload.getBytes(StandardCharsets.UTF_8));
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }
}

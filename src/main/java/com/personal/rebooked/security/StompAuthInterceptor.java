package com.personal.rebooked.security;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.personal.rebooked.auth.jwt.JwtService;
import com.personal.rebooked.conversation.ChatEvents;
import com.personal.rebooked.conversation.ChatException;
import com.personal.rebooked.user.models.User;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.MessagingException;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.messaging.support.MessageHeaderAccessor;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Component;

import java.util.Date;
import java.util.Map;

/**
 * Authenticates the socket on STOMP CONNECT (browser SockJS can't send an Authorization header on the handshake),
 * limits SUBSCRIBE to the caller's private queue, and rejects SENDs once the token has expired.
 * Anything thrown here reaches the client as a STOMP ERROR frame.
 */
@Component
@RequiredArgsConstructor
public class StompAuthInterceptor implements ChannelInterceptor {
    private static final String TOKEN_EXP = "tokenExp";

    private final JwtService jwtService;
    private final UserDetailsService userDetailsService;
    private final ObjectMapper objectMapper;
    // Lazy: the messaging template is built from the broker config this interceptor is part of
    private final ObjectProvider<SimpMessagingTemplate> messagingTemplate;

    @Override
    public Message<?> preSend(Message<?> message, MessageChannel channel) {
        StompHeaderAccessor accessor = MessageHeaderAccessor.getAccessor(message, StompHeaderAccessor.class);
        if (accessor == null || accessor.getCommand() == null) return message;

        switch (accessor.getCommand()) {
            case CONNECT -> authenticate(accessor);
            case SUBSCRIBE -> {
                requireUser(accessor);
                String destination = accessor.getDestination();
                if (destination == null || !destination.startsWith("/user/queue/")) {
                    throw new MessagingException("Forbidden subscription: " + destination);
                }
            }
            case SEND -> {
                requireUser(accessor);
                if (tokenExpired(accessor)) {
                    // Keep the session open; the client refreshes its token and reconnects
                    messagingTemplate.getObject().convertAndSendToUser(accessor.getUser().getName(), ChatEvents.QUEUE,
                            ChatEvents.error(clientIdOf(message), ChatException.Code.TOKEN_EXPIRED,
                                    "Your session has expired. Reconnect with a new token.", null));
                    return null;
                }
            }
            default -> { }
        }
        return message;
    }

    private void authenticate(StompHeaderAccessor accessor) {
        String header = accessor.getFirstNativeHeader("Authorization");
        if (header == null || !header.startsWith("Bearer ")) {
            throw new MessagingException("Unauthorized");
        }
        String token = header.substring(7).trim();
        try {
            User user = (User) userDetailsService.loadUserByUsername(jwtService.extractUsername(token));
            if (!jwtService.isValidToken(token, user)) throw new MessagingException("Unauthorized");
            Date expiration = jwtService.extractClaim(token, Claims::getExpiration);
            accessor.setUser(new StompPrincipal(user.getId()));
            Map<String, Object> session = accessor.getSessionAttributes();
            if (session != null) session.put(TOKEN_EXP, expiration.getTime());
        } catch (JwtException | IllegalArgumentException | UsernameNotFoundException | ClassCastException e) {
            throw new MessagingException("Unauthorized");
        }
    }

    private static void requireUser(StompHeaderAccessor accessor) {
        if (accessor.getUser() == null) throw new MessagingException("Unauthorized");
    }

    private static boolean tokenExpired(StompHeaderAccessor accessor) {
        Map<String, Object> session = accessor.getSessionAttributes();
        Object exp = session != null ? session.get(TOKEN_EXP) : null;
        return exp instanceof Long time && time < System.currentTimeMillis();
    }

    private String clientIdOf(Message<?> message) {
        if (!(message.getPayload() instanceof byte[] bytes) || bytes.length == 0) return null;
        try {
            JsonNode node = objectMapper.readTree(bytes);
            return node.hasNonNull("clientId") ? node.get("clientId").asText() : null;
        } catch (Exception e) {
            return null;
        }
    }
}

package com.personal.rebooked.conversation;

import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.messaging.SessionConnectedEvent;
import org.springframework.web.socket.messaging.SessionDisconnectEvent;
import org.springframework.messaging.simp.SimpMessageHeaderAccessor;

import java.security.Principal;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/** Which users have a connected socket. In memory, so it only covers this instance. */
@Component
public class ChatSessions {
    private final Map<String, String> userBySession = new ConcurrentHashMap<>();
    private final Map<String, Integer> sessionCount = new ConcurrentHashMap<>();

    @EventListener
    public void onConnected(SessionConnectedEvent event) {
        Principal user = event.getUser();
        String sessionId = SimpMessageHeaderAccessor.getSessionId(event.getMessage().getHeaders());
        if (user == null || sessionId == null) return;
        if (userBySession.putIfAbsent(sessionId, user.getName()) == null) {
            sessionCount.merge(user.getName(), 1, Integer::sum);
        }
    }

    @EventListener
    public void onDisconnect(SessionDisconnectEvent event) {
        String userId = userBySession.remove(event.getSessionId());
        if (userId != null) {
            sessionCount.computeIfPresent(userId, (id, count) -> count > 1 ? count - 1 : null);
        }
    }

    public boolean isOnline(String userId) {
        return sessionCount.containsKey(userId);
    }
}

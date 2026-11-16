package com.personal.rebooked.notification;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.personal.rebooked.conversation.ChatEvents;
import com.personal.rebooked.conversation.ChatException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.Message;
import org.springframework.messaging.converter.MessageConversionException;
import org.springframework.messaging.handler.annotation.MessageExceptionHandler;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.messaging.simp.annotation.SendToUser;
import org.springframework.stereotype.Controller;

import java.security.Principal;
import java.util.List;
import java.util.Map;

/** /app/notification.read: { clientId, ids } or { clientId, all: true }. Naturally idempotent. */
@Slf4j
@Controller
@RequiredArgsConstructor
public class NotificationGateway {
    public record ReadPayload(String clientId, List<String> ids, Boolean all) {}

    private final InAppNotifications inApp;
    private final ObjectMapper objectMapper;

    @MessageMapping("/notification.read")
    @SendToUser(destinations = ChatEvents.QUEUE, broadcast = false)
    public Map<String, Object> read(@Payload ReadPayload payload, Principal principal) {
        if (payload.clientId() == null || payload.clientId().isBlank()) {
            throw ChatException.invalid("clientId", "clientId is required");
        }
        boolean all = Boolean.TRUE.equals(payload.all());
        boolean hasIds = payload.ids() != null && !payload.ids().isEmpty();
        if (all == hasIds) throw ChatException.invalid("ids", "Send either ids or all: true");
        long unread = inApp.markRead(principal.getName(), hasIds ? payload.ids() : List.of(), all);
        return ChatEvents.ack(payload.clientId(), Map.of("unreadTotal", unread));
    }

    @MessageExceptionHandler
    @SendToUser(destinations = ChatEvents.QUEUE, broadcast = false)
    public Map<String, Object> handle(Throwable ex, Message<?> message) {
        String clientId = clientIdOf(message);
        if (ex instanceof ChatException chat) {
            return ChatEvents.error(clientId, chat.getCode(), chat.getMessage(), chat.getErrors());
        }
        if (ex instanceof MessageConversionException) {
            return ChatEvents.error(clientId, ChatException.Code.VALIDATION, "Malformed message", null);
        }
        log.error("Notification command failed", ex);
        return ChatEvents.error(clientId, ChatException.Code.SERVER, "Something went wrong", null);
    }

    private String clientIdOf(Message<?> message) {
        try {
            Object payload = message.getPayload();
            JsonNode node = payload instanceof byte[] bytes ? objectMapper.readTree(bytes) : objectMapper.valueToTree(payload);
            return node != null && node.hasNonNull("clientId") ? node.get("clientId").asText() : null;
        } catch (Exception e) {
            return null;
        }
    }
}

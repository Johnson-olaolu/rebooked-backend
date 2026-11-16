package com.personal.rebooked.conversation;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.personal.rebooked.conversation.dto.BookSoldPayload;
import com.personal.rebooked.conversation.dto.ReadPayload;
import com.personal.rebooked.conversation.dto.SendMessagePayload;
import com.personal.rebooked.conversation.dto.StartConversationPayload;
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
import java.util.Map;

/**
 * Chat commands (/app/...). Each one is acknowledged on the sender's own session only;
 * failures come back as an "error" event and never close the session.
 */
@Slf4j
@Controller
@RequiredArgsConstructor
public class ConversationGateway {
    private final ConversationService conversationService;
    private final ObjectMapper objectMapper;

    @MessageMapping("/conversation.start")
    @SendToUser(destinations = ChatEvents.QUEUE, broadcast = false)
    public Map<String, Object> start(@Payload StartConversationPayload payload, Principal principal) {
        return ChatEvents.ack(payload.clientId(), conversationService.start(principal.getName(), payload));
    }

    @MessageMapping("/message.send")
    @SendToUser(destinations = ChatEvents.QUEUE, broadcast = false)
    public Map<String, Object> send(@Payload SendMessagePayload payload, Principal principal) {
        return ChatEvents.ack(payload.clientId(), conversationService.send(principal.getName(), payload));
    }

    @MessageMapping("/conversation.read")
    @SendToUser(destinations = ChatEvents.QUEUE, broadcast = false)
    public Map<String, Object> read(@Payload ReadPayload payload, Principal principal) {
        return ChatEvents.ack(payload.clientId(), conversationService.read(principal.getName(), payload));
    }

    @MessageMapping("/book.sold")
    @SendToUser(destinations = ChatEvents.QUEUE, broadcast = false)
    public Map<String, Object> bookSold(@Payload BookSoldPayload payload, Principal principal) {
        return ChatEvents.ack(payload.clientId(), conversationService.bookSold(principal.getName(), payload));
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
        log.error("Chat command failed", ex);
        return ChatEvents.error(clientId, ChatException.Code.SERVER, "Something went wrong", null);
    }

    private String clientIdOf(Message<?> message) {
        try {
            Object payload = message.getPayload();
            JsonNode node = payload instanceof byte[] bytes ? objectMapper.readTree(bytes)
                    : objectMapper.valueToTree(payload);
            return node != null && node.hasNonNull("clientId") ? node.get("clientId").asText() : null;
        } catch (Exception e) {
            return null;
        }
    }
}

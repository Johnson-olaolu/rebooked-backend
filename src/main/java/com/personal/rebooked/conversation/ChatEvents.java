package com.personal.rebooked.conversation;

import com.personal.rebooked.conversation.dto.ConversationView;
import com.personal.rebooked.conversation.models.Message;

import java.util.Date;
import java.util.LinkedHashMap;
import java.util.Map;

/** Server-to-client events on /user/queue/events. */
public final class ChatEvents {
    public static final String QUEUE = "/queue/events";

    private ChatEvents() {}

    public static Map<String, Object> ack(String clientId, Object data) {
        Map<String, Object> event = new LinkedHashMap<>();
        event.put("type", "ack");
        event.put("clientId", clientId);
        event.put("data", data);
        return event;
    }

    public static Map<String, Object> message(Message message, ConversationView conversation) {
        Map<String, Object> event = new LinkedHashMap<>();
        event.put("type", "message");
        event.put("conversationId", message.getConversationId());
        event.put("message", message);
        event.put("conversation", conversation);
        return event;
    }

    public static Map<String, Object> read(String conversationId, String userId, String upToMessageId, Date readAt) {
        Map<String, Object> event = new LinkedHashMap<>();
        event.put("type", "read");
        event.put("conversationId", conversationId);
        event.put("userId", userId);
        event.put("upToMessageId", upToMessageId);
        event.put("readAt", readAt);
        return event;
    }

    public static Map<String, Object> conversation(ConversationView conversation) {
        Map<String, Object> event = new LinkedHashMap<>();
        event.put("type", "conversation");
        event.put("conversation", conversation);
        return event;
    }

    public static Map<String, Object> error(String clientId, ChatException.Code code, String message,
                                            Map<String, String> errors) {
        Map<String, Object> event = new LinkedHashMap<>();
        event.put("type", "error");
        event.put("clientId", clientId);
        event.put("code", code);
        event.put("message", message);
        if (errors != null && !errors.isEmpty()) event.put("errors", errors);
        return event;
    }
}

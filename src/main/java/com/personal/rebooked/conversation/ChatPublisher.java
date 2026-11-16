package com.personal.rebooked.conversation;

import com.personal.rebooked.conversation.dto.ConversationView;
import com.personal.rebooked.conversation.models.Conversation;
import com.personal.rebooked.conversation.models.Message;
import com.personal.rebooked.notification.NotificationService;
import com.personal.rebooked.notification.Notifications;
import com.personal.rebooked.utils.Constants;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Component;

/** Pushes events to each participant's private queue, built for that participant. */
@Slf4j
@Component
@RequiredArgsConstructor
public class ChatPublisher {
    private final SimpMessagingTemplate messagingTemplate;
    private final ChatSessions sessions;
    private final ConversationViews views;
    private final NotificationService notificationService;
    private final Notifications notifications;

    public void send(String userId, Object event) {
        messagingTemplate.convertAndSendToUser(userId, ChatEvents.QUEUE, event);
    }

    /** A "message" event to both users; a grouped NEW_MESSAGES notification if the recipient isn't connected. */
    public void message(Conversation conversation, Message message) {
        for (String userId : conversation.getParticipantIds()) {
            send(userId, ChatEvents.message(message, views.of(conversation, userId)));
            if (!userId.equals(message.getSenderId()) && message.getType() == Constants.MessageType.TEXT
                    && !sessions.isOnline(userId)) {
                String preview = conversation.getLastMessage() != null ? conversation.getLastMessage().getPreview() : null;
                notificationService.send(notifications.newMessages(userId, message.getSenderId(),
                        Notifications.snapshot(conversation.book(message.getBookId()).orElse(null)),
                        conversation.getId(), preview));
            }
        }
    }

    /** A "conversation" event to both users. */
    public void conversation(Conversation conversation) {
        for (String userId : conversation.getParticipantIds()) {
            send(userId, ChatEvents.conversation(views.of(conversation, userId)));
        }
    }
}

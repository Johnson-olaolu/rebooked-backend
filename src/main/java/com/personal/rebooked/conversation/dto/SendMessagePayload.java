package com.personal.rebooked.conversation.dto;

/** /app/message.send */
public record SendMessagePayload(String clientId, String conversationId, String bookId, String body) {}

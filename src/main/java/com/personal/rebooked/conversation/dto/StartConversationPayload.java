package com.personal.rebooked.conversation.dto;

/** /app/conversation.start */
public record StartConversationPayload(String clientId, String bookId, String body) {}

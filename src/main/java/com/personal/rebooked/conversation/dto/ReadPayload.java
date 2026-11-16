package com.personal.rebooked.conversation.dto;

/** /app/conversation.read */
public record ReadPayload(String clientId, String conversationId, String upToMessageId) {}

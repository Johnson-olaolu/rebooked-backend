package com.personal.rebooked.conversation.dto;

/** /app/book.sold */
public record BookSoldPayload(String clientId, String conversationId, String bookId) {}

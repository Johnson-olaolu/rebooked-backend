package com.personal.rebooked.conversation.dto;

import java.util.Date;

/** GET /book/{id}/chat-buyers: someone the seller can mark the book as sold to. */
public record ChatBuyer(String userId, String userName, String profilePictureUrl, String conversationId,
                        Date lastMessageAt) {
}

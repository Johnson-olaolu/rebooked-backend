package com.personal.rebooked.conversation.dto;

import com.personal.rebooked.conversation.models.LastMessage;
import com.personal.rebooked.user.dto.PublicUserDTO;

import java.util.Date;
import java.util.List;

/** A conversation as one participant sees it: used for list rows, the detail view and events. */
public record ConversationView(String id, PublicUserDTO other, List<ConversationBookView> books,
                               LastMessage lastMessage, int unread, String otherLastReadMessageId,
                               Date createdAt, Date updatedAt) {}

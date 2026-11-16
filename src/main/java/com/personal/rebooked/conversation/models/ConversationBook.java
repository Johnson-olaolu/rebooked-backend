package com.personal.rebooked.conversation.models;

import com.personal.rebooked.utils.Constants;
import lombok.Data;

import java.util.Date;

/**
 * A book attached to a conversation. Roles are per book, so A can sell to B and buy from B in one conversation.
 * title and coverUrl are a snapshot, so the conversation still shows the book after it's deleted.
 */
@Data
public class ConversationBook {
    private String bookId;
    private String sellerId;
    private String buyerId;
    private String title;
    private String coverUrl;
    private Date addedAt;
    private String addedBy;
    private Constants.ConversationBookState state = Constants.ConversationBookState.OPEN;
    private Date lastMessageAt;
}

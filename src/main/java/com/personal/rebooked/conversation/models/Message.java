package com.personal.rebooked.conversation.models;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.personal.rebooked.utils.Constants;
import lombok.Data;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.mapping.Document;

import java.util.Date;

/**
 * The unique { senderId, clientId } index is partial (only messages with a clientId), so it's created in
 * ConversationService rather than here.
 */
@Data
@JsonInclude(JsonInclude.Include.NON_NULL)
@Document("messages")
@CompoundIndex(name = "conversation_id_desc", def = "{'conversationId': 1, '_id': -1}")
public class Message {
    @Id
    private String id;

    private String conversationId;

    /** On SYSTEM messages, the user whose action caused them. */
    private String senderId;

    /** Which attached book this is about, if any. */
    private String bookId;

    private Constants.MessageType type;

    /** TEXT only: 1-2000 characters, trimmed. */
    private String body;

    /** SYSTEM only. */
    private Constants.SystemEvent systemEvent;

    /** Set by the client, for idempotent retries. TEXT only. */
    private String clientId;

    @CreatedDate
    private Date createdAt;
}

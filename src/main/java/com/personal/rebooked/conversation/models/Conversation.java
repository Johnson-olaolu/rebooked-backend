package com.personal.rebooked.conversation.models;

import lombok.Data;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.util.*;

/** One conversation per pair of users; every book they talk about is attached to it. */
@Data
@Document("conversations")
@CompoundIndex(name = "participants_last_message", def = "{'participantIds': 1, 'lastMessageAt': -1}")
public class Conversation {
    @Id
    private String id;

    /** Exactly two user ids, sorted. */
    private List<String> participantIds;

    /** "<minId>_<maxId>", used for find-or-create. */
    @Indexed(unique = true)
    private String pairKey;

    private List<ConversationBook> books = new ArrayList<>();

    private LastMessage lastMessage;

    @Indexed
    private Date lastMessageAt;

    /** Keyed by user id. */
    private Map<String, ReadState> readState = new HashMap<>();

    @CreatedDate
    private Date createdAt;

    @LastModifiedDate
    private Date updatedAt;

    public static String pairKey(String a, String b) {
        return a.compareTo(b) < 0 ? a + "_" + b : b + "_" + a;
    }

    public String otherParticipant(String userId) {
        return participantIds.get(0).equals(userId) ? participantIds.get(1) : participantIds.get(0);
    }

    public Optional<ConversationBook> book(String bookId) {
        if (books == null || bookId == null) return Optional.empty();
        return books.stream().filter(b -> bookId.equals(b.getBookId())).findFirst();
    }

    public ReadState readStateOf(String userId) {
        ReadState state = readState != null ? readState.get(userId) : null;
        return state != null ? state : new ReadState();
    }
}

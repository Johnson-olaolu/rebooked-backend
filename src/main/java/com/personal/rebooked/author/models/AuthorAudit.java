package com.personal.rebooked.author.models;

import lombok.Data;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.index.CompoundIndexes;
import org.springframework.data.mongodb.core.mapping.Document;

import java.util.Date;
import java.util.Map;

/** Every admin, merge and import change to an author. before/after hold only the changed fields. */
@Data
@Document("author_audit")
@CompoundIndexes({
        @CompoundIndex(name = "author_created", def = "{'authorId': 1, 'createdAt': -1}")
})
public class AuthorAudit {
    @Id
    private String id;

    private String authorId;

    /** A user id, or "system" for the import. */
    private String actorId;

    private Action action;

    private Map<String, Object> before;

    private Map<String, Object> after;

    private String reason;

    private Date createdAt;

    public enum Action {
        CREATED, UPDATED, VERIFIED, MERGED, EXTERNAL_ID_ADDED, EXTERNAL_ID_REMOVED, KEPT_SEPARATE, DELETED
    }
}

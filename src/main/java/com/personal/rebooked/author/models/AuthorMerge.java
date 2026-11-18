package com.personal.rebooked.author.models;

import lombok.Data;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.util.Date;

/**
 * A merge of fromId into intoId. Mongo here has no transactions, so a merge is a job whose steps can each run
 * twice; Seeder resumes any merge that didn't reach COMPLETED.
 */
@Data
@Document("author_merges")
public class AuthorMerge {
    @Id
    private String id;

    private String fromId;

    private String intoId;

    /** A user id, or "system" for an Open Library redirect. */
    private String actorId;

    private String reason;

    @Indexed(name = "state")
    private State state;

    private long booksUpdated;

    private Date createdAt;

    private Date completedAt;

    public enum State { STARTED, BOOKS_UPDATED, IDENTITY_MOVED, COMPLETED }
}

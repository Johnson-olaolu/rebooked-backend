package com.personal.rebooked.author.models;

import lombok.Data;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.index.CompoundIndexes;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.util.Date;

/**
 * The admin review queue (§23f). The unique partial index on { type, pairKey } is created in
 * AuthorService.ensureIndexes().
 */
@Data
@Document("author_review_items")
@CompoundIndexes({
        @CompoundIndex(name = "status_type_created", def = "{'status': 1, 'type': 1, 'createdAt': -1}")
})
public class AuthorReviewItem {
    @Id
    private String id;

    private Type type;

    /** The author under review. */
    @Indexed(name = "author_id")
    private String authorId;

    /** The possible duplicate (pair types only). */
    @Indexed(name = "other_author_id", sparse = true)
    private String otherAuthorId;

    /** "<minId>_<maxId>" for pair types. */
    private String pairKey;

    private String reason;

    private ItemStatus status = ItemStatus.OPEN;

    private Resolution resolution;

    private String resolvedBy;

    private Date resolvedAt;

    private Date createdAt;

    public enum Type {
        PROPOSAL, POSSIBLE_DUPLICATE, MIGRATION_AMBIGUOUS;

        public boolean pair() {
            return this != PROPOSAL;
        }
    }

    public enum ItemStatus { OPEN, RESOLVED }

    public enum Resolution { VERIFIED, MERGED, KEPT_SEPARATE, DELETED }

    public static String pairKey(String a, String b) {
        return a.compareTo(b) <= 0 ? a + "_" + b : b + "_" + a;
    }
}

package com.personal.rebooked.author;

import com.personal.rebooked.author.models.Author;
import com.personal.rebooked.author.models.AuthorReviewItem;
import com.personal.rebooked.author.models.AuthorReviewItem.ItemStatus;
import com.personal.rebooked.author.models.AuthorReviewItem.Resolution;
import com.personal.rebooked.author.models.AuthorReviewItem.Type;
import com.personal.rebooked.author.repositories.AuthorRepository;
import com.personal.rebooked.author.repositories.AuthorReviewItemRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.stereotype.Component;

import java.util.Date;

import static org.springframework.data.mongodb.core.query.Criteria.where;
import static org.springframework.data.mongodb.core.query.Query.query;

/** Adds and resolves items in the admin review queue (§23f). */
@Component
@RequiredArgsConstructor
public class AuthorReviewQueue {
    private final AuthorReviewItemRepository items;
    private final AuthorRepository authorRepository;
    private final MongoTemplate mongoTemplate;

    /** A PROPOSAL item for a seller-proposed author; at most one open per author. */
    public void addProposal(String authorId, String reason) {
        if (items.existsByAuthorIdAndTypeAndStatus(authorId, Type.PROPOSAL, ItemStatus.OPEN)) return;
        AuthorReviewItem item = new AuthorReviewItem();
        item.setType(Type.PROPOSAL);
        item.setAuthorId(authorId);
        item.setReason(reason);
        item.setCreatedAt(new Date());
        items.insert(item);
    }

    /**
     * A pair item (POSSIBLE_DUPLICATE or MIGRATION_AMBIGUOUS). Skipped when the pair was ever kept separate, or when
     * it's already queued with this type. A POSSIBLE_DUPLICATE is also skipped when both authors were imported: Open
     * Library resolves its own duplicates, and many people share a name. Returns whether an item was added.
     */
    public boolean addPair(Type type, String authorId, String otherAuthorId, String reason) {
        if (authorId == null || otherAuthorId == null || authorId.equals(otherAuthorId)) return false;
        String pairKey = AuthorReviewItem.pairKey(authorId, otherAuthorId);
        if (items.existsByPairKeyAndResolution(pairKey, Resolution.KEPT_SEPARATE)) return false;
        Author a = authorRepository.findById(authorId).orElse(null);
        Author b = authorRepository.findById(otherAuthorId).orElse(null);
        if (a == null || b == null) return false;
        if (type == Type.POSSIBLE_DUPLICATE
                && a.getOrigin() == Author.Origin.IMPORTED && b.getOrigin() == Author.Origin.IMPORTED) return false;

        AuthorReviewItem item = new AuthorReviewItem();
        item.setType(type);
        item.setAuthorId(authorId);
        item.setOtherAuthorId(otherAuthorId);
        item.setPairKey(pairKey);
        item.setReason(reason);
        item.setCreatedAt(new Date());
        try {
            items.insert(item);
            return true;
        } catch (DuplicateKeyException e) {
            return false; // already queued (the unique { type, pairKey } index)
        }
    }

    /** Resolves the author's open PROPOSAL item. */
    public void resolveProposal(String authorId, Resolution resolution, String actorId) {
        mongoTemplate.updateMulti(query(where("authorId").is(authorId).and("type").is(Type.PROPOSAL)
                        .and("status").is(ItemStatus.OPEN)),
                resolved(resolution, actorId), AuthorReviewItem.class);
    }

    /** Resolves every open item that involves the author, on either side. */
    public void resolveAllFor(String authorId, Resolution resolution, String actorId) {
        mongoTemplate.updateMulti(query(new Criteria().orOperator(where("authorId").is(authorId),
                                where("otherAuthorId").is(authorId)).and("status").is(ItemStatus.OPEN)),
                resolved(resolution, actorId), AuthorReviewItem.class);
    }

    public void resolve(AuthorReviewItem item, Resolution resolution, String actorId) {
        mongoTemplate.updateFirst(query(where("id").is(item.getId())), resolved(resolution, actorId), AuthorReviewItem.class);
    }

    private static Update resolved(Resolution resolution, String actorId) {
        return new Update().set("status", ItemStatus.RESOLVED).set("resolution", resolution)
                .set("resolvedBy", actorId).set("resolvedAt", new Date());
    }
}

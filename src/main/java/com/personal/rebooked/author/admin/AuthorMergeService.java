package com.personal.rebooked.author.admin;

import com.mongodb.client.MongoCollection;
import com.mongodb.client.model.Filters;
import com.mongodb.client.model.Projections;
import com.mongodb.client.model.Updates;
import com.personal.rebooked.author.AuthorAuditLog;
import com.personal.rebooked.author.AuthorNames;
import com.personal.rebooked.author.AuthorReviewQueue;
import com.personal.rebooked.author.AuthorStats;
import com.personal.rebooked.author.models.Author;
import com.personal.rebooked.author.models.AuthorAudit;
import com.personal.rebooked.author.models.AuthorExternalIdentifier;
import com.personal.rebooked.author.models.AuthorMerge;
import com.personal.rebooked.author.models.AuthorReviewItem;
import com.personal.rebooked.author.repositories.AuthorMergeRepository;
import com.personal.rebooked.author.repositories.AuthorRepository;
import com.personal.rebooked.exception.ApiException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.bson.Document;
import org.bson.types.ObjectId;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.util.*;

import static org.springframework.data.mongodb.core.query.Criteria.where;
import static org.springframework.data.mongodb.core.query.Query.query;

/**
 * Merges one author into another (§23f). Mongo here is a standalone server with no transactions, so a merge is a
 * job in author_merges whose steps can each safely run twice; Seeder resumes unfinished merges at startup. The
 * merged record is never deleted: old ids and slugs keep resolving through mergedIntoAuthorId.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AuthorMergeService {
    private final AuthorRepository authorRepository;
    private final AuthorMergeRepository merges;
    private final AuthorStats authorStats;
    private final AuthorReviewQueue reviewQueue;
    private final AuthorAuditLog auditLog;
    private final MongoTemplate mongoTemplate;

    /** Merges fromId into intoId and returns the surviving author. */
    public Author merge(String fromId, String intoId, String reason, String actorId) {
        if (reason == null || reason.isBlank()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Validation failed", Map.of("reason", "Give a reason for the merge"));
        }
        if (intoId == null || intoId.isBlank()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Validation failed", Map.of("intoId", "Choose the author to merge into"));
        }
        Author from = require(fromId);
        Author into = require(intoId);
        checkMergeable(from, into);

        AuthorMerge job = new AuthorMerge();
        job.setFromId(from.getId());
        job.setIntoId(into.getId());
        job.setActorId(actorId == null ? AuthorAuditLog.SYSTEM : actorId);
        job.setReason(reason.trim());
        job.setState(AuthorMerge.State.STARTED);
        job.setCreatedAt(new Date());
        job = merges.insert(job);
        run(job);
        return authorRepository.findById(into.getId()).orElseThrow();
    }

    /** 409 when the two can't be merged: the same author, or either already merged. */
    public static void checkMergeable(Author from, Author into) {
        if (from.getId().equals(into.getId())) {
            throw new ApiException(HttpStatus.CONFLICT, "An author can't be merged into itself");
        }
        if (from.getStatus() == Author.Status.MERGED || into.getStatus() == Author.Status.MERGED) {
            throw new ApiException(HttpStatus.CONFLICT, "One of these authors has already been merged");
        }
    }

    /** Runs the remaining steps of a merge. */
    public void run(AuthorMerge job) {
        if (job.getState() == AuthorMerge.State.STARTED) {
            job.setBooksUpdated(job.getBooksUpdated() + moveBooks(job.getFromId(), job.getIntoId()));
            job.setState(AuthorMerge.State.BOOKS_UPDATED);
            job = merges.save(job);
        }
        if (job.getState() == AuthorMerge.State.BOOKS_UPDATED) {
            moveIdentity(job.getFromId(), job.getIntoId());
            job.setState(AuthorMerge.State.IDENTITY_MOVED);
            job = merges.save(job);
        }
        if (job.getState() == AuthorMerge.State.IDENTITY_MOVED) {
            retire(job);
            job.setState(AuthorMerge.State.COMPLETED);
            job.setCompletedAt(new Date());
            merges.save(job);
            log.info("Merged author {} into {} ({} books updated) by {}", job.getFromId(), job.getIntoId(),
                    job.getBooksUpdated(), job.getActorId());
        }
    }

    /** Startup: finish any merge interrupted by a restart. */
    public void resumeIncomplete() {
        for (AuthorMerge job : merges.findByStateNot(AuthorMerge.State.COMPLETED)) {
            try {
                log.info("Resuming author merge {} ({} into {}) from {}", job.getId(), job.getFromId(), job.getIntoId(), job.getState());
                run(job);
            } catch (Exception e) {
                log.error("Couldn't resume author merge {}", job.getId(), e);
            }
        }
    }

    /** Steps 2–3: books listing both lose the from entry; every other book swaps from for into. */
    private long moveBooks(String fromId, String intoId) {
        Author into = authorRepository.findById(intoId).orElseThrow();
        MongoCollection<Document> books = mongoTemplate.getCollection("book");
        long updated = 0;
        for (Document book : books.find(Filters.all("authors.authorId", List.of(fromId, intoId)))
                .projection(Projections.include("authors"))) {
            List<Document> kept = new ArrayList<>();
            for (Document entry : book.getList("authors", Document.class, List.of())) {
                if (fromId.equals(entry.getString("authorId"))) continue;
                kept.add(new Document(entry).append("position", kept.size()));
            }
            books.updateOne(Filters.eq("_id", book.get("_id")), Updates.set("authors", kept));
            updated++;
        }
        updated += mongoTemplate.updateMulti(query(where("authors.authorId").is(fromId)),
                new Update().set("authors.$[a].authorId", intoId)
                        .set("authors.$[a].name", into.getName())
                        .set("authors.$[a].slug", into.getSlug())
                        .filterArray(where("a.authorId").is(fromId)),
                "book").getModifiedCount();
        return updated;
    }

    /** Step 4: from's aliases and name become into's aliases; external ids move; into's search keys follow. */
    private void moveIdentity(String fromId, String intoId) {
        Author from = authorRepository.findById(fromId).orElseThrow();
        Author into = authorRepository.findById(intoId).orElseThrow();

        List<Author.Alias> aliases = new ArrayList<>(into.aliasList());
        Set<String> known = new HashSet<>();
        known.add(into.getNormalizedName());
        aliases.forEach(a -> known.add(a.getNormalizedName()));
        for (Author.Alias alias : from.aliasList()) {
            if (known.add(alias.getNormalizedName())) aliases.add(alias);
        }
        String fromName = AuthorNames.normalize(from.getName());
        if (known.add(fromName)) aliases.add(new Author.Alias(from.getName(), fromName, Author.Source.ADMIN));

        mongoTemplate.updateMulti(query(where("authorId").is(fromId)), Update.update("authorId", intoId),
                AuthorExternalIdentifier.class);
        mongoTemplate.updateFirst(query(where("id").is(intoId)), new Update()
                        .set("aliases", aliases)
                        .set("searchKeys", AuthorNames.searchKeys(into.getName(),
                                aliases.stream().map(Author.Alias::getName).toList()))
                        .set("updatedAt", new Date()),
                Author.class);
    }

    /** Step 5: from is MERGED, earlier merges into from now point at into, the queue and counts catch up. */
    private void retire(AuthorMerge job) {
        String fromId = job.getFromId();
        String intoId = job.getIntoId();
        Author from = authorRepository.findById(fromId).orElseThrow();
        Author.Status previous = from.getStatus();

        mongoTemplate.updateFirst(query(where("id").is(fromId)), new Update()
                .set("status", Author.Status.MERGED).set("mergedIntoAuthorId", intoId)
                .set("availableCount", 0L).set("soldCount", 0L).set("updatedAt", new Date()), Author.class);
        // No chains: authors merged into from now point straight at into
        mongoTemplate.updateMulti(query(where("mergedIntoAuthorId").is(fromId)),
                Update.update("mergedIntoAuthorId", intoId), Author.class);
        reviewQueue.resolveAllFor(fromId, AuthorReviewItem.Resolution.MERGED, job.getActorId());
        authorStats.recount(List.of(fromId, intoId));

        Map<String, Object> before = new LinkedHashMap<>();
        before.put("status", previous);
        Map<String, Object> after = new LinkedHashMap<>();
        after.put("status", Author.Status.MERGED);
        after.put("mergedIntoAuthorId", intoId);
        auditLog.record(fromId, job.getActorId(), AuthorAudit.Action.MERGED, before, after, job.getReason());
        auditLog.record(intoId, job.getActorId(), AuthorAudit.Action.MERGED, null,
                Map.of("mergedFromAuthorId", fromId, "booksUpdated", job.getBooksUpdated()), job.getReason());
    }

    private Author require(String id) {
        if (id == null || !ObjectId.isValid(id)) throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Author not found");
        return authorRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Author not found"));
    }
}

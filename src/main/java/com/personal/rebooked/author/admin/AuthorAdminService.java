package com.personal.rebooked.author.admin;

import com.personal.rebooked.author.AuthorAuditLog;
import com.personal.rebooked.author.AuthorNames;
import com.personal.rebooked.author.AuthorReviewQueue;
import com.personal.rebooked.author.AuthorSearch;
import com.personal.rebooked.author.AuthorService;
import com.personal.rebooked.author.admin.dto.*;
import com.personal.rebooked.author.models.Author;
import com.personal.rebooked.author.models.AuthorAudit;
import com.personal.rebooked.author.models.AuthorExternalIdentifier;
import com.personal.rebooked.author.models.AuthorExternalIdentifier.IdSource;
import com.personal.rebooked.author.models.AuthorReviewItem;
import com.personal.rebooked.author.repositories.AuthorExternalIdentifierRepository;
import com.personal.rebooked.author.repositories.AuthorRepository;
import com.personal.rebooked.author.repositories.AuthorReviewItemRepository;
import com.personal.rebooked.book.models.Book;
import com.personal.rebooked.exception.ApiException;
import com.personal.rebooked.user.dto.PublicUserDTO;
import com.personal.rebooked.user.models.User;
import com.personal.rebooked.user.repositories.UserRepository;
import com.personal.rebooked.utils.Misc;
import lombok.RequiredArgsConstructor;
import org.bson.types.ObjectId;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.time.Year;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

import static org.springframework.data.mongodb.core.query.Criteria.where;
import static org.springframework.data.mongodb.core.query.Query.query;

/** The superAdmin author area (§23f): list, edit, verify, external ids, review queue, audit, merge and delete. */
@Service
@RequiredArgsConstructor
public class AuthorAdminService {
    private static final int DEFAULT_PAGE_SIZE = 20;
    private static final int MAX_PAGE_SIZE = 50;
    private static final int MAX_ALIASES = 30;
    private static final int MAX_BIOGRAPHY = 10_000;
    private static final int MAX_DESCRIPTION = 200;

    private final AuthorRepository authorRepository;
    private final AuthorExternalIdentifierRepository identifiers;
    private final AuthorReviewItemRepository reviewItems;
    private final AuthorService authorService;
    private final AuthorReviewQueue reviewQueue;
    private final AuthorAuditLog auditLog;
    private final AuthorMergeService mergeService;
    private final UserRepository userRepository;
    private final MongoTemplate mongoTemplate;

    // ---------------------------------------------------------------- list and detail

    public AdminAuthorPage list(String q, String status, String origin, Integer page, Integer pageSize) {
        Map<String, String> errors = new LinkedHashMap<>();
        Author.Status statusFilter = parseEnum(Author.Status.class, status, "status", errors);
        Author.Origin originFilter = parseEnum(Author.Origin.class, origin, "origin", errors);
        throwIfErrors(errors);

        List<Criteria> common = new ArrayList<>();
        if (q != null && !q.isBlank()) {
            String trimmed = q.trim();
            String folded = AuthorNames.fold(trimmed);
            if (ObjectId.isValid(trimmed)) common.add(where("id").is(trimmed));
            else if (!folded.isEmpty()) common.add(AuthorSearch.keyPrefix(folded));
        }
        if (originFilter != null) common.add(where("origin").is(originFilter));

        int p = page(page);
        int size = pageSize(pageSize);
        List<Criteria> listCriteria = new ArrayList<>(common);
        if (statusFilter != null) listCriteria.add(where("status").is(statusFilter));
        Query listQuery = new Query(and(listCriteria));
        long total = mongoTemplate.count(listQuery, Author.class);
        List<Author> authors = mongoTemplate.find(Query.of(listQuery)
                .with(Sort.by(Sort.Direction.DESC, "updatedAt")).skip((long) p * size).limit(size), Author.class);

        Map<String, Long> counts = new LinkedHashMap<>();
        for (Author.Status s : Author.Status.values()) {
            List<Criteria> withStatus = new ArrayList<>(common);
            withStatus.add(where("status").is(s));
            counts.put(s.name(), mongoTemplate.count(new Query(and(withStatus)), Author.class));
        }

        List<String> ids = authors.stream().map(Author::getId).toList();
        Map<String, Long> openItems = openReviewItemCounts(ids);
        Map<String, List<IdSource>> sources = new HashMap<>();
        for (AuthorExternalIdentifier id : identifiers.findByAuthorIdIn(ids)) {
            List<IdSource> list = sources.computeIfAbsent(id.getAuthorId(), k -> new ArrayList<>());
            if (!list.contains(id.getSource())) list.add(id.getSource());
        }
        List<AdminAuthorRow> rows = authors.stream()
                .map(a -> AdminAuthorRow.of(a, openItems.getOrDefault(a.getId(), 0L), sources.getOrDefault(a.getId(), List.of())))
                .toList();
        return new AdminAuthorPage(rows, p, size, total, (int) ((total + size - 1) / size), counts);
    }

    public AdminAuthorDetail detail(String id) {
        return detail(require(id));
    }

    private AdminAuthorDetail detail(Author a) {
        List<ExternalIdView> externalIds = identifiers.findByAuthorId(a.getId()).stream()
                .sorted(Comparator.comparing(AuthorExternalIdentifier::getSource))
                .map(ExternalIdView::of).toList();
        List<AdminAuthorDetail.ReviewItemBrief> open = new ArrayList<>();
        reviewItems.findByAuthorIdAndStatus(a.getId(), AuthorReviewItem.ItemStatus.OPEN)
                .forEach(i -> open.add(AdminAuthorDetail.ReviewItemBrief.of(i)));
        reviewItems.findByOtherAuthorIdAndStatus(a.getId(), AuthorReviewItem.ItemStatus.OPEN)
                .forEach(i -> open.add(AdminAuthorDetail.ReviewItemBrief.of(i)));
        PublicUserDTO createdBy = a.getCreatedBy() == null ? null
                : userRepository.findById(a.getCreatedBy()).map(PublicUserDTO::from).orElse(PublicUserDTO.deleted(a.getCreatedBy()));
        return new AdminAuthorDetail(a.getId(), a.getName(), a.getSlug(), a.getNormalizedName(), a.verified(),
                a.getStatus(), a.getOrigin(), a.getMergedIntoAuthorId(), a.getBiography(), a.getDescription(),
                a.getBirthYear(), a.getDeathYear(), a.getPhoto(), a.getLinks(), a.getCountries(), a.getWorkCount(),
                a.getAvailableCount(), a.getSoldCount(), a.getFieldSources(),
                a.aliasList().stream().map(al -> new AdminAuthorDetail.Alias(al.getName(), al.getSource())).toList(),
                externalIds, createdBy, open, a.getCreatedAt(), a.getUpdatedAt(), null);
    }

    // ---------------------------------------------------------------- edit

    /** Only the sent fields; each changed one becomes admin-owned. A rename refreshes the book cache. Audited. */
    public AdminAuthorDetail patch(String id, AuthorPatch patch, String actorId) {
        Author a = require(id);
        requireNotMerged(a);
        if (patch == null) return detail(a);
        Map<String, String> errors = new LinkedHashMap<>();
        Map<String, Object> before = new LinkedHashMap<>();
        Map<String, Object> after = new LinkedHashMap<>();
        Update update = new Update();

        String name = a.getName();
        if (patch.name() != null) {
            String newName = AuthorNames.clean(patch.name());
            String error = AuthorService.validateName(newName);
            if (error != null) {
                errors.put("name", error);
            } else if (!newName.equals(a.getName())) {
                changed("name", a.getName(), newName, before, after);
                name = newName;
                update.set("name", newName).set("normalizedName", AuthorNames.normalize(newName))
                        .set("fieldSources.name", Author.Source.ADMIN);
            }
        }

        List<Author.Alias> aliases = a.aliasList();
        if (patch.aliases() != null) {
            List<Author.Alias> next = buildAliases(patch.aliases(), name, a.aliasList(), errors);
            if (next != null && !aliasNames(next).equals(a.aliasNames())) {
                changed("aliases", a.aliasNames(), aliasNames(next), before, after);
                aliases = next;
                update.set("aliases", next);
            }
        }

        if (patch.biography() != null) {
            String bio = Misc.stripHtml(patch.biography());
            if (bio != null && bio.length() > MAX_BIOGRAPHY) {
                errors.put("biography", "Biography must be at most " + MAX_BIOGRAPHY + " characters");
            } else if (!Objects.equals(bio, a.getBiography())) {
                changed("biography", a.getBiography(), bio, before, after);
                update.set("biography", bio).set("fieldSources.biography", Author.Source.ADMIN);
            }
        }

        if (patch.description() != null) {
            String description = Misc.stripHtml(patch.description());
            if (description != null && description.length() > MAX_DESCRIPTION) {
                errors.put("description", "Description must be at most " + MAX_DESCRIPTION + " characters");
            } else if (!Objects.equals(description, a.getDescription())) {
                changed("description", a.getDescription(), description, before, after);
                update.set("description", description).set("fieldSources.description", Author.Source.ADMIN);
            }
        }

        Integer birth = a.getBirthYear();
        Integer death = a.getDeathYear();
        if (patch.birthYear() != null) {
            if (!plausibleYear(patch.birthYear())) errors.put("birthYear", "Enter a year between -3000 and " + Year.now().getValue());
            else birth = patch.birthYear();
        }
        if (patch.deathYear() != null) {
            if (!plausibleYear(patch.deathYear())) errors.put("deathYear", "Enter a year between -3000 and " + Year.now().getValue());
            else death = patch.deathYear();
        }
        if (birth != null && death != null && death < birth && !errors.containsKey("birthYear")) {
            errors.putIfAbsent("deathYear", "The death year can't be before the birth year");
        }
        throwIfErrors(errors);
        if (!Objects.equals(birth, a.getBirthYear())) {
            changed("birthYear", a.getBirthYear(), birth, before, after);
            update.set("birthYear", birth).set("fieldSources.birthYear", Author.Source.ADMIN);
        }
        if (!Objects.equals(death, a.getDeathYear())) {
            changed("deathYear", a.getDeathYear(), death, before, after);
            update.set("deathYear", death).set("fieldSources.deathYear", Author.Source.ADMIN);
        }

        if (after.isEmpty()) return detail(a);
        boolean renamed = after.containsKey("name");
        if (renamed || after.containsKey("aliases")) {
            update.set("searchKeys", AuthorNames.searchKeys(name, aliasNames(aliases)));
        }
        update.set("updatedAt", new Date());
        // A targeted $set, so a concurrent recount of the book counts isn't overwritten
        mongoTemplate.updateFirst(query(where("id").is(a.getId())), update, Author.class);
        Author saved = require(a.getId());
        if (renamed) authorService.refreshBookCache(saved);
        auditLog.record(a.getId(), actorId, AuthorAudit.Action.UPDATED, before, after, null);
        return detail(saved);
    }

    /** null when invalid (errors.aliases set). Keeps the source of aliases that were already there. */
    private static List<Author.Alias> buildAliases(List<String> raw, String name, List<Author.Alias> current,
                                                   Map<String, String> errors) {
        if (raw.size() > MAX_ALIASES) {
            errors.put("aliases", "Up to " + MAX_ALIASES + " aliases");
            return null;
        }
        Map<String, Author.Alias> existing = new HashMap<>();
        current.forEach(al -> existing.put(al.getNormalizedName(), al));
        String nameKey = AuthorNames.normalize(name);
        LinkedHashMap<String, Author.Alias> next = new LinkedHashMap<>();
        for (String value : raw) {
            String alias = AuthorNames.clean(value);
            String error = AuthorService.validateName(alias);
            if (error != null) {
                errors.put("aliases", "Each alias: " + error.substring(0, 1).toLowerCase(Locale.ROOT) + error.substring(1));
                return null;
            }
            String key = AuthorNames.normalize(alias);
            if (key.equals(nameKey) || next.containsKey(key)) continue;
            Author.Alias kept = existing.get(key);
            next.put(key, kept != null && kept.getName().equals(alias) ? kept
                    : new Author.Alias(alias, key, Author.Source.ADMIN));
        }
        return new ArrayList<>(next.values());
    }

    public AdminAuthorDetail verify(String id, String actorId) {
        Author a = require(id);
        requireNotMerged(a);
        if (a.getStatus() != Author.Status.VERIFIED) {
            mongoTemplate.updateFirst(query(where("id").is(id)),
                    new Update().set("status", Author.Status.VERIFIED).set("updatedAt", new Date()), Author.class);
            auditLog.record(id, actorId, AuthorAudit.Action.VERIFIED, Map.of("status", a.getStatus()),
                    Map.of("status", Author.Status.VERIFIED), null);
        }
        reviewQueue.resolveProposal(id, AuthorReviewItem.Resolution.VERIFIED, actorId);
        return detail(id);
    }

    public AdminAuthorDetail addExternalId(String id, ExternalIdPayload payload, String actorId) {
        Author a = require(id);
        requireNotMerged(a);
        Map<String, String> errors = new LinkedHashMap<>();
        IdSource source = parseEnum(IdSource.class, payload == null ? null : payload.source(), "source", errors);
        if (source == null && !errors.containsKey("source")) errors.put("source", "Source is required");
        String sourceId = source == null ? null : source.normalize(payload.sourceId());
        if (source != null && sourceId == null) errors.put("sourceId", "That isn't a valid " + source + " id");
        throwIfErrors(errors);

        Optional<AuthorExternalIdentifier> existing = identifiers.findBySourceAndSourceId(source, sourceId);
        if (existing.isPresent()) {
            if (existing.get().getAuthorId().equals(id)) return detail(a);
            throw alreadyLinked(existing.get());
        }
        try {
            identifiers.insert(new AuthorExternalIdentifier(id, source, sourceId, Author.Source.ADMIN));
        } catch (DuplicateKeyException e) {
            throw identifiers.findBySourceAndSourceId(source, sourceId).map(this::alreadyLinked).orElseThrow(() -> e);
        }
        Update links = new Update().set("updatedAt", new Date());
        Author.Links current = a.getLinks() == null ? new Author.Links() : a.getLinks();
        if (source == IdSource.OPEN_LIBRARY && current.getOpenLibrary() == null) links.set("links.openLibrary", source.url(sourceId));
        if (source == IdSource.WIKIDATA && current.getWikidata() == null) links.set("links.wikidata", source.url(sourceId));
        mongoTemplate.updateFirst(query(where("id").is(id)), links, Author.class);
        auditLog.record(id, actorId, AuthorAudit.Action.EXTERNAL_ID_ADDED, null,
                Map.of("source", source, "sourceId", sourceId), null);
        return detail(id);
    }

    private ApiException alreadyLinked(AuthorExternalIdentifier identifier) {
        String owner = authorRepository.findById(identifier.getAuthorId()).map(Author::getName).orElse("another author");
        String message = "Already linked to " + owner;
        return new ApiException(HttpStatus.CONFLICT, message, Map.of("sourceId", message));
    }

    public AdminAuthorDetail removeExternalId(String id, String source, String sourceId, String actorId) {
        require(id);
        IdSource parsed;
        try {
            parsed = IdSource.valueOf(source.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Identifier not found");
        }
        String normalized = parsed.normalize(sourceId);
        AuthorExternalIdentifier identifier = normalized == null ? null
                : identifiers.findBySourceAndSourceId(parsed, normalized).filter(i -> i.getAuthorId().equals(id)).orElse(null);
        if (identifier == null) throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Identifier not found");
        identifiers.delete(identifier);
        auditLog.record(id, actorId, AuthorAudit.Action.EXTERNAL_ID_REMOVED,
                Map.of("source", parsed, "sourceId", normalized), null, null);
        return detail(id);
    }

    // ---------------------------------------------------------------- books and audit

    /** Books in any status, drafts included. Each carries its owner's public summary (seller). */
    public AdminPage<Book> books(String id, Integer page, Integer pageSize) {
        require(id);
        int p = page(page);
        int size = pageSize(pageSize);
        Query q = query(where("authors.authorId").is(id));
        long total = mongoTemplate.count(q, Book.class);
        List<Book> books = mongoTemplate.find(Query.of(q).with(Sort.by(Sort.Direction.DESC, "updatedAt"))
                .skip((long) p * size).limit(size), Book.class);
        return AdminPage.of(books, p, size, total);
    }

    public AdminPage<AuditEntryView> audit(String id, Integer page, Integer pageSize) {
        require(id);
        int p = page(page);
        int size = pageSize(pageSize);
        Query q = query(where("authorId").is(id));
        long total = mongoTemplate.count(q, AuthorAudit.class);
        List<AuthorAudit> entries = mongoTemplate.find(Query.of(q).with(Sort.by(Sort.Direction.DESC, "createdAt"))
                .skip((long) p * size).limit(size), AuthorAudit.class);
        Map<String, PublicUserDTO> actors = users(entries.stream().map(AuthorAudit::getActorId).toList());
        List<AuditEntryView> views = entries.stream()
                .map(e -> new AuditEntryView(e.getId(), e.getAction(), e.getBefore(), e.getAfter(), e.getReason(),
                        e.getCreatedAt(), e.getActorId(), actors.get(e.getActorId())))
                .toList();
        return AdminPage.of(views, p, size, total);
    }

    // ---------------------------------------------------------------- review queue

    /** Oldest first. status defaults to OPEN. */
    public AdminPage<ReviewItemView> reviewItems(String type, String status, Integer page, Integer pageSize) {
        Map<String, String> errors = new LinkedHashMap<>();
        AuthorReviewItem.Type typeFilter = parseEnum(AuthorReviewItem.Type.class, type, "type", errors);
        AuthorReviewItem.ItemStatus statusFilter = parseEnum(AuthorReviewItem.ItemStatus.class, status, "status", errors);
        throwIfErrors(errors);
        if (statusFilter == null) statusFilter = AuthorReviewItem.ItemStatus.OPEN;

        int p = page(page);
        int size = pageSize(pageSize);
        Criteria criteria = where("status").is(statusFilter);
        if (typeFilter != null) criteria = criteria.and("type").is(typeFilter);
        Query q = new Query(criteria);
        long total = mongoTemplate.count(q, AuthorReviewItem.class);
        List<AuthorReviewItem> items = mongoTemplate.find(Query.of(q).with(Sort.by(Sort.Direction.ASC, "createdAt"))
                .skip((long) p * size).limit(size), AuthorReviewItem.class);

        Set<String> authorIds = new HashSet<>();
        items.forEach(i -> {
            authorIds.add(i.getAuthorId());
            if (i.getOtherAuthorId() != null) authorIds.add(i.getOtherAuthorId());
        });
        Map<String, Author> authors = new HashMap<>();
        authorRepository.findAllById(authorIds).forEach(a -> authors.put(a.getId(), a));
        Map<String, List<ExternalIdView>> ids = new HashMap<>();
        for (AuthorExternalIdentifier id : identifiers.findByAuthorIdIn(authorIds)) {
            ids.computeIfAbsent(id.getAuthorId(), k -> new ArrayList<>()).add(ExternalIdView.of(id));
        }
        Function<String, ReviewItemView.ReviewAuthor> view = authorId -> authorId == null ? null
                : ReviewItemView.ReviewAuthor.of(authors.get(authorId), ids.getOrDefault(authorId, List.of()));
        List<ReviewItemView> views = items.stream()
                .map(i -> new ReviewItemView(i.getId(), i.getType(), i.getStatus(), i.getResolution(), i.getReason(),
                        i.getCreatedAt(), i.getResolvedAt(), view.apply(i.getAuthorId()), view.apply(i.getOtherAuthorId())))
                .toList();
        return AdminPage.of(views, p, size, total);
    }

    public ReviewItemView keepSeparate(String itemId, KeepSeparatePayload payload, String actorId) {
        AuthorReviewItem item = itemId == null || !ObjectId.isValid(itemId) ? null : reviewItems.findById(itemId).orElse(null);
        if (item == null) throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Review item not found");
        if (item.getStatus() == AuthorReviewItem.ItemStatus.RESOLVED) {
            throw new ApiException(HttpStatus.CONFLICT, "This item is already resolved");
        }
        if (!item.getType().pair()) {
            throw new ApiException(HttpStatus.CONFLICT, "Only a pair of authors can be kept separate; verify or merge a proposal");
        }
        String reason = payload == null || payload.reason() == null || payload.reason().isBlank() ? null : payload.reason().trim();
        reviewQueue.resolve(item, AuthorReviewItem.Resolution.KEPT_SEPARATE, actorId);
        auditLog.record(item.getAuthorId(), actorId, AuthorAudit.Action.KEPT_SEPARATE, null,
                Map.of("otherAuthorId", item.getOtherAuthorId()), reason);
        auditLog.record(item.getOtherAuthorId(), actorId, AuthorAudit.Action.KEPT_SEPARATE, null,
                Map.of("otherAuthorId", item.getAuthorId()), reason);
        AuthorReviewItem resolved = reviewItems.findById(itemId).orElse(item);
        List<ReviewItemView> views = reviewItemViews(List.of(resolved));
        return views.get(0);
    }

    private List<ReviewItemView> reviewItemViews(List<AuthorReviewItem> items) {
        List<ReviewItemView> views = new ArrayList<>();
        for (AuthorReviewItem i : items) {
            Function<String, ReviewItemView.ReviewAuthor> view = authorId -> authorId == null ? null
                    : ReviewItemView.ReviewAuthor.of(authorRepository.findById(authorId).orElse(null),
                    identifiers.findByAuthorId(authorId).stream().map(ExternalIdView::of).toList());
            views.add(new ReviewItemView(i.getId(), i.getType(), i.getStatus(), i.getResolution(), i.getReason(),
                    i.getCreatedAt(), i.getResolvedAt(), view.apply(i.getAuthorId()), view.apply(i.getOtherAuthorId())));
        }
        return views;
    }

    // ---------------------------------------------------------------- merge and delete

    public MergePreview mergePreview(String id, String intoId) {
        Author from = require(id);
        Author into = require(intoId);
        AuthorMergeService.checkMergeable(from, into);
        List<Book> shared = mongoTemplate.find(query(where("authors.authorId").all(from.getId(), into.getId())), Book.class);
        return new MergePreview(detail(from).withBookCount(bookCount(from.getId())),
                detail(into).withBookCount(bookCount(into.getId())), shared);
    }

    public AdminAuthorDetail merge(String id, MergePayload payload, String actorId) {
        Author survivor = mergeService.merge(id, payload == null ? null : payload.intoId(),
                payload == null ? null : payload.reason(), actorId);
        return detail(survivor);
    }

    /** Only when no book lists the author and nothing was merged into it. Audited. */
    public void delete(String id, String actorId) {
        Author a = require(id);
        if (a.getStatus() == Author.Status.MERGED) {
            throw new ApiException(HttpStatus.CONFLICT, "A merged author is kept so its old links keep working");
        }
        if (bookCount(id) > 0) throw new ApiException(HttpStatus.CONFLICT, "This author is on books. Merge it instead.");
        if (mongoTemplate.exists(query(where("mergedIntoAuthorId").is(id)), Author.class)) {
            throw new ApiException(HttpStatus.CONFLICT, "Other authors were merged into this one. Merge it instead.");
        }
        identifiers.deleteByAuthorId(id);
        reviewQueue.resolveAllFor(id, AuthorReviewItem.Resolution.DELETED, actorId);
        Map<String, Object> before = new LinkedHashMap<>();
        before.put("name", a.getName());
        before.put("slug", a.getSlug());
        before.put("status", a.getStatus());
        before.put("origin", a.getOrigin());
        auditLog.record(id, actorId, AuthorAudit.Action.DELETED, before, null, null);
        authorRepository.deleteById(id);
    }

    // ---------------------------------------------------------------- helpers

    private long bookCount(String authorId) {
        return mongoTemplate.count(query(where("authors.authorId").is(authorId)), Book.class);
    }

    private Map<String, Long> openReviewItemCounts(List<String> authorIds) {
        if (authorIds.isEmpty()) return Map.of();
        Query open = query(new Criteria().orOperator(where("authorId").in(authorIds), where("otherAuthorId").in(authorIds))
                .and("status").is(AuthorReviewItem.ItemStatus.OPEN));
        open.fields().include("authorId", "otherAuthorId");
        Map<String, Long> counts = new HashMap<>();
        Set<String> wanted = new HashSet<>(authorIds);
        for (AuthorReviewItem item : mongoTemplate.find(open, AuthorReviewItem.class)) {
            if (wanted.contains(item.getAuthorId())) counts.merge(item.getAuthorId(), 1L, Long::sum);
            if (item.getOtherAuthorId() != null && wanted.contains(item.getOtherAuthorId())) {
                counts.merge(item.getOtherAuthorId(), 1L, Long::sum);
            }
        }
        return counts;
    }

    private Map<String, PublicUserDTO> users(Collection<String> ids) {
        List<String> valid = ids.stream().filter(Objects::nonNull).filter(ObjectId::isValid).distinct().toList();
        if (valid.isEmpty()) return Map.of();
        return userRepository.findAllById(valid).stream()
                .collect(Collectors.toMap(User::getId, PublicUserDTO::from, (a, b) -> a));
    }

    private Author require(String id) {
        if (id == null || !ObjectId.isValid(id)) throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Author not found");
        return authorRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Author not found"));
    }

    private static void requireNotMerged(Author a) {
        if (a.getStatus() == Author.Status.MERGED) {
            throw new ApiException(HttpStatus.CONFLICT, "This author was merged into another one");
        }
    }

    private static boolean plausibleYear(int year) {
        return year >= -3000 && year <= Year.now().getValue();
    }

    private static void changed(String field, Object before, Object after, Map<String, Object> b, Map<String, Object> a) {
        b.put(field, before);
        a.put(field, after);
    }

    private static List<String> aliasNames(List<Author.Alias> aliases) {
        return aliases.stream().map(Author.Alias::getName).toList();
    }

    private static <E extends Enum<E>> E parseEnum(Class<E> type, String raw, String field, Map<String, String> errors) {
        if (raw == null || raw.isBlank()) return null;
        try {
            return Enum.valueOf(type, raw.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            errors.put(field, field.substring(0, 1).toUpperCase(Locale.ROOT) + field.substring(1) + " must be one of "
                    + Arrays.stream(type.getEnumConstants()).map(Enum::name).collect(Collectors.joining(", ")));
            return null;
        }
    }

    private static Criteria and(List<Criteria> criteria) {
        return criteria.isEmpty() ? new Criteria() : new Criteria().andOperator(criteria.toArray(new Criteria[0]));
    }

    private static int page(Integer page) {
        return page == null || page < 0 ? 0 : page;
    }

    private static int pageSize(Integer pageSize) {
        return pageSize == null || pageSize < 1 ? DEFAULT_PAGE_SIZE : Math.min(pageSize, MAX_PAGE_SIZE);
    }

    private static void throwIfErrors(Map<String, String> errors) {
        if (!errors.isEmpty()) throw new ApiException(HttpStatus.BAD_REQUEST, "Validation failed", errors);
    }
}

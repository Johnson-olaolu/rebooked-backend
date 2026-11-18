package com.personal.rebooked.author;

import com.personal.rebooked.author.dto.AuthorDetail;
import com.personal.rebooked.author.dto.AuthorSummary;
import com.personal.rebooked.author.dto.ProposalPayload;
import com.personal.rebooked.author.dto.ProposalResult;
import com.personal.rebooked.author.models.Author;
import com.personal.rebooked.author.models.Author.Origin;
import com.personal.rebooked.author.models.Author.Source;
import com.personal.rebooked.author.models.Author.Status;
import com.personal.rebooked.author.models.AuthorReviewItem;
import com.personal.rebooked.author.repositories.AuthorRepository;
import com.personal.rebooked.book.models.BookAuthor;
import com.personal.rebooked.exception.ApiException;
import com.personal.rebooked.service.RedisService;
import com.personal.rebooked.utils.Misc;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.bson.types.ObjectId;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.index.Index;
import org.springframework.data.mongodb.core.index.PartialIndexFilter;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.springframework.data.mongodb.core.query.Criteria.where;
import static org.springframework.data.mongodb.core.query.Query.query;

/**
 * The author catalogue (§23a–c): creating authors, following merges, resolving a book's authors, and the public
 * author endpoints.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AuthorService {
    public static final int MAX_AUTHORS_PER_BOOK = 10;
    public static final int MAX_MERGE_HOPS = 5;
    static final String TOO_MANY_REQUESTS = "Too many requests, try again shortly";

    private static final int SEARCH_DEFAULT = 10;
    private static final int SEARCH_MAX = 20;
    private static final int QUERY_MAX_LENGTH = 80;
    private static final int TOP_DEFAULT = 8;
    private static final int TOP_MAX = 20;
    private static final int SEARCH_PER_MINUTE = 60;
    private static final int PROPOSALS_PER_DAY = 20;
    private static final int BOOK_SEARCH_MAX_AUTHORS = 500;

    private static final Pattern URL_LIKE = Pattern.compile(
            "(?i)(\\w+://|www\\.)|^[\\w-]+(\\.[\\w-]+)*\\.(com|net|org|io|ng|co|uk|info|biz|app|dev|xyz)(/\\S*)?$");
    private static final Pattern EMAIL_LIKE = Pattern.compile("\\S+@\\S+\\.\\S+");
    private static final Pattern HAS_LETTER = Pattern.compile("\\p{L}");

    private final AuthorRepository authorRepository;
    private final AuthorSearch authorSearch;
    private final AuthorReviewQueue reviewQueue;
    private final MongoTemplate mongoTemplate;
    private final RedisService redisService;

    /** Off until someone reviews the Open Library and Wikimedia reuse terms. */
    @Value("${authors.display.external-bios:false}")
    private boolean showExternalBios;

    @Value("${authors.display.external-photos:false}")
    private boolean showExternalPhotos;

    /** Indexes that annotations can't express (partial ones). */
    @PostConstruct
    void ensureIndexes() {
        // Authors with books on Rebooked. A different key from the plain searchKeys index, so Mongo accepts both.
        mongoTemplate.indexOps(Author.class).ensureIndex(new Index()
                .on("searchKeys", Sort.Direction.ASC).on("availableCount", Sort.Direction.DESC)
                .named("search_keys_with_books")
                .partial(PartialIndexFilter.of(where("availableCount").gt(0))));
        // Alias matches in proposals, the migration and the duplicate pass
        mongoTemplate.indexOps(Author.class).ensureIndex(new Index()
                .on("aliases.normalizedName", Sort.Direction.ASC).named("alias_normalized_name"));
        mongoTemplate.indexOps(AuthorReviewItem.class).ensureIndex(new Index()
                .on("type", Sort.Direction.ASC).on("pairKey", Sort.Direction.ASC)
                .unique().named("type_pair_unique")
                .partial(PartialIndexFilter.of(where("pairKey").exists(true))));
    }

    // ---------------------------------------------------------------- public endpoints

    /** GET /author/search */
    public List<AuthorSummary> search(String q, Integer limit, String who) {
        rateLimit("ratelimit:author-search:" + who, SEARCH_PER_MINUTE, 1, TimeUnit.MINUTES);
        String raw = q == null ? "" : q.trim();
        String folded = AuthorNames.fold(raw);
        if (raw.length() > QUERY_MAX_LENGTH) {
            throw validation("q", "Search must be at most " + QUERY_MAX_LENGTH + " characters");
        }
        if (folded.length() < 2) throw validation("q", "Type at least 2 characters");
        int size = limit == null || limit < 1 ? SEARCH_DEFAULT : Math.min(limit, SEARCH_MAX);
        return authorSearch.search(folded).stream()
                .limit(size)
                .map(hit -> AuthorSummary.of(hit.author(), hit.matchedAlias()))
                .toList();
    }

    /** GET /author/top: most-shelved authors. */
    public List<AuthorSummary> top(Integer limit) {
        int size = limit == null || limit < 1 ? TOP_DEFAULT : Math.min(limit, TOP_MAX);
        Query top = query(where("availableCount").gt(0).and("status").ne(Status.MERGED))
                .with(Sort.by(Sort.Order.desc("availableCount"), Sort.Order.asc("name")))
                .limit(size);
        return mongoTemplate.find(top, Author.class).stream().map(AuthorSummary::of).toList();
    }

    /** GET /author/{id}: a merged author answers with its canonical author. */
    public AuthorDetail detailById(String id) {
        return detail(findCanonical(id).orElseThrow(AuthorService::notFound));
    }

    /** GET /author/slug/{slug} */
    public AuthorDetail detailBySlug(String slug) {
        Author author = slug == null ? null : authorRepository.findBySlug(slug.trim().toLowerCase(Locale.ROOT)).orElse(null);
        Author canonical = canonical(author);
        if (canonical == null) throw notFound();
        return detail(canonical);
    }

    public record ProposalOutcome(HttpStatus status, ProposalResult result) {}

    /** POST /author/proposals */
    public ProposalOutcome propose(String userId, ProposalPayload payload) {
        rateLimit("ratelimit:author-proposals:" + userId, PROPOSALS_PER_DAY, 1, TimeUnit.DAYS);
        String name = AuthorNames.clean(payload == null ? null : payload.name());
        String error = validateName(name);
        if (error != null) throw validation("name", error);

        List<Author> candidates = proposalCandidates(name);
        boolean force = payload != null && Boolean.TRUE.equals(payload.force());
        if (!candidates.isEmpty() && !force) {
            return new ProposalOutcome(HttpStatus.OK,
                    new ProposalResult(null, candidates.stream().map(AuthorSummary::of).toList()));
        }

        Author author = create(name, Origin.USER_PROPOSED, Status.UNVERIFIED, Source.USER, userId);
        reviewQueue.addProposal(author.getId(), "Proposed by a seller");
        for (Author candidate : candidates) {
            reviewQueue.addPair(AuthorReviewItem.Type.POSSIBLE_DUPLICATE, author.getId(), candidate.getId(),
                    "Added by a seller despite a similar existing author");
        }
        return new ProposalOutcome(HttpStatus.CREATED, new ProposalResult(AuthorSummary.of(author), List.of()));
    }

    /** null when the name is acceptable for a new author; otherwise the field error. */
    public static String validateName(String name) {
        if (name == null || name.length() < 2 || name.length() > 120) {
            return "Enter the author's name (2 to 120 characters)";
        }
        if (!HAS_LETTER.matcher(name).find()) return "The name must contain a letter";
        if (URL_LIKE.matcher(name).find() || EMAIL_LIKE.matcher(name).find()) {
            return "Enter a name, not a link or an email address";
        }
        return null;
    }

    // ---------------------------------------------------------------- books

    /**
     * Turns the authorIds sent with a book into its authors, in the order sent (§23b). Merged ids are replaced by
     * their canonical author. Puts errors.authorIds and returns null when the list isn't acceptable.
     */
    public List<BookAuthor> resolveForBook(List<String> ids, Map<String, String> errors) {
        if (ids == null) return null;
        if (ids.size() > MAX_AUTHORS_PER_BOOK * 5) {
            errors.put("authorIds", "Up to " + MAX_AUTHORS_PER_BOOK + " authors");
            return null;
        }
        Set<String> valid = new LinkedHashSet<>();
        for (String id : ids) {
            if (id == null || !ObjectId.isValid(id.trim())) {
                errors.put("authorIds", "One of these authors no longer exists");
                return null;
            }
            valid.add(id.trim());
        }
        Map<String, Author> byId = new HashMap<>();
        authorRepository.findAllById(valid).forEach(a -> byId.put(a.getId(), a));

        List<Author> resolved = new ArrayList<>();
        for (String id : ids) {
            Author canonical = canonical(byId.get(id.trim()));
            if (canonical == null) {
                errors.put("authorIds", "One of these authors no longer exists");
                return null;
            }
            resolved.add(canonical);
        }
        Set<String> seen = new HashSet<>();
        for (Author author : resolved) {
            if (!seen.add(author.getId())) {
                errors.put("authorIds", "Add each author once");
                return null;
            }
        }
        if (resolved.size() > MAX_AUTHORS_PER_BOOK) {
            errors.put("authorIds", "Up to " + MAX_AUTHORS_PER_BOOK + " authors");
            return null;
        }
        List<BookAuthor> authors = new ArrayList<>();
        for (int i = 0; i < resolved.size(); i++) {
            Author a = resolved.get(i);
            authors.add(new BookAuthor(a.getId(), i, a.getName(), a.getSlug()));
        }
        return authors;
    }

    /** Authors matched by a shop search: up to 500 ids, plus which were exact (tier 0) or prefix (tiers 1–2). */
    public record BookSearchAuthors(List<String> ids, List<String> exactIds, List<String> prefixIds) {
        public static final BookSearchAuthors NONE = new BookSearchAuthors(List.of(), List.of(), List.of());
    }

    public BookSearchAuthors authorsForBookSearch(String text) {
        String folded = AuthorNames.fold(text);
        if (folded.isEmpty()) return BookSearchAuthors.NONE;
        List<String> ids = new ArrayList<>();
        List<String> exact = new ArrayList<>();
        List<String> prefix = new ArrayList<>();
        for (AuthorSearch.Hit hit : authorSearch.search(folded)) {
            if (ids.size() >= BOOK_SEARCH_MAX_AUTHORS || hit.tier() > 2) break;
            ids.add(hit.author().getId());
            if (hit.tier() == 0) exact.add(hit.author().getId());
            else prefix.add(hit.author().getId());
        }
        return new BookSearchAuthors(ids, exact, prefix);
    }

    // ---------------------------------------------------------------- shared

    /** Follows mergedIntoAuthorId (at most 5 hops). null for null, or for a chain that doesn't end. */
    public Author canonical(Author author) {
        Author current = author;
        for (int hops = 0; current != null && current.getStatus() == Status.MERGED; hops++) {
            if (hops >= MAX_MERGE_HOPS || current.getMergedIntoAuthorId() == null) return null;
            current = authorRepository.findById(current.getMergedIntoAuthorId()).orElse(null);
        }
        return current;
    }

    public Optional<Author> findCanonical(String id) {
        if (id == null || !ObjectId.isValid(id)) return Optional.empty();
        return Optional.ofNullable(canonical(authorRepository.findById(id).orElse(null)));
    }

    /**
     * Authors (not MERGED) whose normalizedName or an alias's normalizedName equals the name's. Best first: VERIFIED,
     * then availableCount, then workCount. Used by the migration and the seed.
     */
    public List<Author> sameName(String name) {
        String normalized = AuthorNames.normalize(name);
        return nameCandidates(name).stream()
                .filter(a -> normalized.equals(a.getNormalizedName())
                        || a.aliasList().stream().anyMatch(al -> normalized.equals(al.getNormalizedName())))
                .sorted(BEST_MATCH)
                .toList();
    }

    /** sameName, plus folded matches on the name or an alias: what a seller's proposal is checked against. */
    public List<Author> proposalCandidates(String name) {
        String normalized = AuthorNames.normalize(name);
        String folded = AuthorNames.fold(name);
        return nameCandidates(name).stream()
                .filter(a -> normalized.equals(a.getNormalizedName())
                        || folded.equals(AuthorNames.fold(a.getName()))
                        || a.aliasList().stream().anyMatch(al -> normalized.equals(al.getNormalizedName())
                        || folded.equals(AuthorNames.fold(al.getName()))))
                .sorted(BEST_MATCH)
                .toList();
    }

    public static final Comparator<Author> BEST_MATCH = Comparator
            .comparing((Author a) -> !a.verified())
            .thenComparing(Author::getAvailableCount, Comparator.reverseOrder())
            .thenComparing(Author::getWorkCount, Comparator.reverseOrder());

    private List<Author> nameCandidates(String name) {
        String normalized = AuthorNames.normalize(name);
        String folded = AuthorNames.fold(name);
        if (normalized.isEmpty()) return List.of();
        List<Criteria> any = new ArrayList<>(List.of(
                where("normalizedName").is(normalized),
                where("aliases.normalizedName").is(normalized)));
        // The folded full name or alias is always one of the search keys
        if (!folded.isEmpty()) any.add(where("searchKeys").is(folded));
        Query q = query(new Criteria().orOperator(any.toArray(new Criteria[0])).and("status").ne(Status.MERGED)).limit(200);
        return mongoTemplate.find(q, Author.class);
    }

    /** The best existing author with this name, or a new one. For the seed. */
    public Author findOrCreate(String name, Origin origin, Source nameSource) {
        String clean = AuthorNames.clean(name);
        List<Author> matches = sameName(clean);
        if (!matches.isEmpty()) return matches.get(0);
        return create(clean, origin, Status.UNVERIFIED, nameSource, null);
    }

    /** Inserts a new author with a unique slug (retried if another insert takes it first). */
    public Author create(String name, Origin origin, Status status, Source nameSource, String createdBy) {
        Author author = new Author();
        author.setName(AuthorNames.clean(name));
        author.setNormalizedName(AuthorNames.normalize(name));
        author.setSearchKeys(AuthorNames.searchKeys(name, List.of()));
        author.setOrigin(origin);
        author.setStatus(status);
        author.getFieldSources().put("name", nameSource);
        author.setCreatedBy(createdBy);
        Date now = new Date();
        author.setCreatedAt(now);
        author.setUpdatedAt(now);
        return insertWithSlug(author);
    }

    public Author insertWithSlug(Author author) {
        for (int attempt = 1; ; attempt++) {
            author.setSlug(uniqueSlug(author.getName()));
            try {
                return mongoTemplate.insert(author);
            } catch (DuplicateKeyException e) {
                if (attempt >= 3) throw e;
            }
        }
    }

    /** slugify(name) (falling back to "author"), then -2, -3… if taken. */
    public String uniqueSlug(String name) {
        String base = Misc.slugify(name, "author");
        // base is [a-z0-9-] only, so it needs no escaping; the anchored prefix uses the slug index
        Query taken = query(where("slug").regex("^" + base + "(-[0-9]+)?$"));
        taken.fields().include("slug");
        Pattern suffix = Pattern.compile("^" + Pattern.quote(base) + "-(\\d+)$");
        boolean baseTaken = false;
        long max = 1;
        for (Author a : mongoTemplate.find(taken, Author.class)) {
            if (base.equals(a.getSlug())) baseTaken = true;
            Matcher m = suffix.matcher(a.getSlug());
            if (m.matches() && m.group(1).length() < 10) max = Math.max(max, Long.parseLong(m.group(1)));
        }
        return baseTaken ? base + "-" + (max + 1) : base;
    }

    /** Refreshes the name/slug display cache on every book that lists the author (§23b). */
    public long refreshBookCache(Author author) {
        return mongoTemplate.updateMulti(query(where("authors.authorId").is(author.getId())),
                new Update().set("authors.$[a].name", author.getName())
                        .set("authors.$[a].slug", author.getSlug())
                        .filterArray(where("a.authorId").is(author.getId())),
                "book").getModifiedCount();
    }

    /** The public detail shape, with the licensing gate for external biographies and photos. */
    public AuthorDetail detail(Author a) {
        Source bioSource = a.fieldSource("biography");
        String biography = a.getBiography();
        if (biography != null && bioSource != null && bioSource.external() && !showExternalBios) biography = null;

        String photoUrl = a.getPhoto() == null ? null : a.getPhoto().getUrl();
        if (photoUrl != null && a.getPhoto().getSource() != null && a.getPhoto().getSource().external()
                && !showExternalPhotos) {
            photoUrl = null;
        }

        Author.Links links = a.getLinks() == null ? new Author.Links() : a.getLinks();
        Set<String> attribution = new LinkedHashSet<>();
        addAttribution(attribution, a.fieldSource("name"));
        if (a.getDescription() != null) addAttribution(attribution, a.fieldSource("description"));
        if (a.getBirthYear() != null) addAttribution(attribution, a.fieldSource("birthYear"));
        if (a.getDeathYear() != null) addAttribution(attribution, a.fieldSource("deathYear"));
        if (biography != null) addAttribution(attribution, bioSource);
        if (photoUrl != null) addAttribution(attribution, a.getPhoto().getSource());
        a.aliasList().forEach(alias -> addAttribution(attribution, alias.getSource()));
        if (links.getOpenLibrary() != null) attribution.add(Source.OPEN_LIBRARY.name());
        if (links.getWikidata() != null || links.getWikipedia() != null) attribution.add(Source.WIKIDATA.name());

        return new AuthorDetail(a.getId(), a.getName(), a.getSlug(), a.verified(), a.getBirthYear(), a.getDeathYear(),
                a.getDescription(), a.getAvailableCount(), a.getSoldCount(), biography, photoUrl, a.aliasNames(),
                new AuthorDetail.Links(links.getOpenLibrary(), links.getWikidata(), links.getWikipedia()),
                new ArrayList<>(attribution));
    }

    private static void addAttribution(Set<String> attribution, Source source) {
        if (source != null && source.external()) attribution.add(source.name());
    }

    // ---------------------------------------------------------------- helpers

    /** Fixed-window limit. Fails open if Redis is down: search shouldn't break with it. */
    private void rateLimit(String key, int max, long window, TimeUnit unit) {
        long count;
        try {
            count = redisService.increment(key, window, unit);
        } catch (Exception e) {
            log.warn("Rate limit skipped for {}, Redis unavailable: {}", key, e.toString());
            return;
        }
        if (count > max) throw new ApiException(HttpStatus.TOO_MANY_REQUESTS, TOO_MANY_REQUESTS);
    }

    static ApiException validation(String field, String message) {
        return new ApiException(HttpStatus.BAD_REQUEST, "Validation failed", Map.of(field, message));
    }

    static ResponseStatusException notFound() {
        return new ResponseStatusException(HttpStatus.NOT_FOUND, "Author not found");
    }
}

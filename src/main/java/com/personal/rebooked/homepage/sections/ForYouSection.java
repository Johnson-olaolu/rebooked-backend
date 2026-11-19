package com.personal.rebooked.homepage.sections;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.personal.rebooked.book.BookService;
import com.personal.rebooked.book.models.Book;
import com.personal.rebooked.book.models.BookAuthor;
import com.personal.rebooked.book.saved.SavedBookService;
import com.personal.rebooked.book.views.BookViews;
import com.personal.rebooked.category.models.Category;
import com.personal.rebooked.exception.ApiException;
import com.personal.rebooked.homepage.HomepageBooks;
import com.personal.rebooked.homepage.HomepageCache;
import com.personal.rebooked.homepage.dto.AiPick;
import com.personal.rebooked.homepage.dto.ForYou;
import com.personal.rebooked.homepage.dto.PickRef;
import com.personal.rebooked.service.AiService;
import com.personal.rebooked.user.models.User;
import com.personal.rebooked.utils.Constants;
import lombok.extern.slf4j.Slf4j;
import org.bson.types.ObjectId;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.http.HttpStatus;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.stream.Collectors;

import static org.springframework.data.mongodb.core.query.Criteria.where;

/**
 * §24f GET /homepage/for-you: AI picks from the reader's saved and recently viewed books. Cached 24 h, at most one
 * generation per user per 6 h, 8 s timeout on the model. Without history, or on any failure, the global picks with
 * personalised=false. Prompts carry reading history, so they're never logged.
 */
@Slf4j
@Component
public class ForYouSection {
    static final String KEY_PREFIX = "homepage:ai-picks:user:";
    static final String GATE_PREFIX = "homepage:ai-picks:user-gen:";
    static final String RATE_PREFIX = "ratelimit:for-you:";
    static final Duration TTL = Duration.ofHours(24);
    static final Duration GATE = Duration.ofHours(6);
    static final int RATE_LIMIT = 30;
    static final Duration RATE_WINDOW = Duration.ofMinutes(1);
    static final Duration MODEL_TIMEOUT = Duration.ofSeconds(8);
    static final int TOP = 3;
    static final int POOL_MATCHING = 40;
    static final int POOL_TRENDING = 10;

    public record Stored(List<PickRef> picks, List<String> basedOn) {}

    /** The reader's top categories and authors: ids for the query, names for the prompt and basedOn. */
    record Taste(Map<String, String> categories, Map<String, String> authors) {
        boolean isEmpty() {
            return categories.isEmpty() && authors.isEmpty();
        }

        List<String> names() {
            List<String> names = new ArrayList<>(categories.values());
            names.addAll(authors.values());
            return names;
        }
    }

    private final HomepageCache cache;
    private final AiPicksSection globalPicks;
    private final TrendingSection trending;
    private final BookService bookService;
    private final SavedBookService savedBooks;
    private final BookViews bookViews;
    private final MongoTemplate mongoTemplate;
    private final AiService aiService;
    private final ObjectMapper objectMapper;
    private final ThreadPoolTaskExecutor executor;

    public ForYouSection(HomepageCache cache, AiPicksSection globalPicks, TrendingSection trending, BookService bookService,
                         SavedBookService savedBooks, BookViews bookViews, MongoTemplate mongoTemplate, AiService aiService,
                         ObjectMapper objectMapper, @Qualifier("homepageAiExecutor") ThreadPoolTaskExecutor executor) {
        this.cache = cache;
        this.globalPicks = globalPicks;
        this.trending = trending;
        this.bookService = bookService;
        this.savedBooks = savedBooks;
        this.bookViews = bookViews;
        this.mongoTemplate = mongoTemplate;
        this.aiService = aiService;
        this.objectMapper = objectMapper;
        this.executor = executor;
    }

    public ForYou forYou(User user) {
        if (cache.increment(RATE_PREFIX + user.getId(), RATE_WINDOW) > RATE_LIMIT) {
            throw new ApiException(HttpStatus.TOO_MANY_REQUESTS, "Too many requests, try again shortly");
        }
        Optional<Stored> cached = cache.get(KEY_PREFIX + user.getId(), new TypeReference<Stored>() {});
        if (cached.isPresent()) {
            List<AiPick> picks = globalPicks.resolve(cached.get().picks());
            if (!picks.isEmpty()) return new ForYou(picks, cached.get().basedOn(), true);
        }
        try {
            List<String> savedIds = savedBooks.ids(user.getId());
            List<String> recentIds = bookViews.recentIds(user.getId());
            Taste taste = taste(bookService.booksInOrder(union(savedIds, recentIds)), user.getId());
            if (taste.isEmpty()) return global();
            // One generation per user per 6 h, whatever happens to it
            if (!cache.claim(GATE_PREFIX + user.getId(), GATE)) return global();

            List<Book> pool = pool(user.getId(), taste, union(savedIds, recentIds));
            if (pool.size() < AiPicksParser.MIN_VALID) return global();
            String message = "The reader likes: " + String.join(", ", taste.names()) + ".\n\n"
                    + AiPicksSection.promptJson(pool, objectMapper);
            CompletableFuture<String> call = CompletableFuture.supplyAsync(() -> aiService.pickBooksForReader(message), executor);
            String response;
            try {
                response = call.get(MODEL_TIMEOUT.toMillis(), TimeUnit.MILLISECONDS);
            } catch (TimeoutException e) {
                call.cancel(true);
                log.warn("Personalised picks for {} timed out after {}", user.getId(), MODEL_TIMEOUT);
                return global();
            }
            List<PickRef> picks = AiPicksParser.parse(response, new HashSet<>(HomepageBooks.ids(pool)));
            Stored stored = new Stored(picks, taste.names());
            cache.put(KEY_PREFIX + user.getId(), stored, TTL);
            List<AiPick> resolved = globalPicks.resolve(picks);
            return resolved.isEmpty() ? global() : new ForYou(resolved, stored.basedOn(), true);
        } catch (Exception e) {
            log.warn("Personalised picks for {} failed; serving the global picks: {}", user.getId(), e.toString());
            return global();
        }
    }

    private ForYou global() {
        return new ForYou(globalPicks.read(), List.of(), false);
    }

    /** Top 3 categories and top 3 authors across the reader's saved and viewed books (not their own). */
    static Taste taste(List<Book> history, String userId) {
        Map<String, Long> categoryCounts = new HashMap<>();
        Map<String, String> categoryNames = new HashMap<>();
        Map<String, Long> authorCounts = new HashMap<>();
        Map<String, String> authorNames = new HashMap<>();
        for (Book b : history) {
            if (b.getUser() != null && userId.equals(b.getUser().getId())) continue;
            if (b.getCategories() != null) {
                for (Category c : b.getCategories()) {
                    if (c == null || c.getId() == null) continue;
                    categoryCounts.merge(c.getId(), 1L, Long::sum);
                    categoryNames.put(c.getId(), c.getName());
                }
            }
            if (b.getAuthors() != null) {
                for (BookAuthor a : b.getAuthors()) {
                    if (a == null || a.getAuthorId() == null) continue;
                    authorCounts.merge(a.getAuthorId(), 1L, Long::sum);
                    authorNames.put(a.getAuthorId(), a.getName());
                }
            }
        }
        return new Taste(top(categoryCounts, categoryNames), top(authorCounts, authorNames));
    }

    private static Map<String, String> top(Map<String, Long> counts, Map<String, String> names) {
        return counts.entrySet().stream()
                .sorted(Map.Entry.<String, Long>comparingByValue().reversed().thenComparing(Map.Entry::getKey))
                .limit(TOP)
                .filter(e -> names.get(e.getKey()) != null)
                .collect(Collectors.toMap(Map.Entry::getKey, e -> names.get(e.getKey()), (a, b) -> a, LinkedHashMap::new));
    }

    /** ~40 ACTIVE books in the reader's categories or by their authors, plus 10 trending; never theirs, saved or viewed. */
    private List<Book> pool(String userId, Taste taste, List<String> exclude) {
        List<Criteria> matches = new ArrayList<>();
        List<ObjectId> categoryIds = taste.categories().keySet().stream().filter(ObjectId::isValid).map(ObjectId::new).toList();
        if (!categoryIds.isEmpty()) matches.add(where("categories.$id").in(categoryIds));
        if (!taste.authors().isEmpty()) matches.add(where("authors.authorId").in(taste.authors().keySet()));
        List<ObjectId> excluded = exclude.stream().filter(ObjectId::isValid).map(ObjectId::new).toList();

        List<Book> pool = new ArrayList<>();
        if (!matches.isEmpty()) {
            Criteria criteria = where("status").is(Constants.BookStatus.ACTIVE)
                    .and("user.$id").ne(new ObjectId(userId))
                    .and("_id").nin(excluded)
                    .orOperator(matches.toArray(new Criteria[0]));
            pool.addAll(mongoTemplate.find(new Query(criteria)
                    .with(Sort.by(Sort.Direction.DESC, "createdAt")).limit(POOL_MATCHING), Book.class));
        }

        Set<String> skip = new HashSet<>(exclude);
        HomepageBooks.fill(pool, pool.size() + POOL_TRENDING, trending.read(),
                b -> !skip.contains(b.getId()) && (b.getUser() == null || !userId.equals(b.getUser().getId())));
        return pool;
    }

    private static List<String> union(List<String> a, List<String> b) {
        LinkedHashSet<String> all = new LinkedHashSet<>(a == null ? List.of() : a);
        if (b != null) all.addAll(b);
        return new ArrayList<>(all);
    }
}

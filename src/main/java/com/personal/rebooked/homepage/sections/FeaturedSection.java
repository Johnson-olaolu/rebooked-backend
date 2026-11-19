package com.personal.rebooked.homepage.sections;

import com.fasterxml.jackson.core.type.TypeReference;
import com.personal.rebooked.author.AuthorService;
import com.personal.rebooked.author.dto.AuthorDetail;
import com.personal.rebooked.author.models.Author;
import com.personal.rebooked.book.BookService;
import com.personal.rebooked.book.models.Book;
import com.personal.rebooked.category.CategoryService;
import com.personal.rebooked.category.models.Category;
import com.personal.rebooked.category.repositories.CategoryRepository;
import com.personal.rebooked.exception.ApiException;
import com.personal.rebooked.homepage.HomepageBooks;
import com.personal.rebooked.homepage.HomepageCache;
import com.personal.rebooked.homepage.HomepageWeek;
import com.personal.rebooked.homepage.WeekViews;
import com.personal.rebooked.homepage.dto.FeaturedOverride;
import com.personal.rebooked.homepage.dto.FeaturedView;
import com.personal.rebooked.homepage.dto.ScoredAuthor;
import com.personal.rebooked.homepage.models.FeatureSource;
import com.personal.rebooked.homepage.models.HomepageFeature;
import com.personal.rebooked.homepage.repositories.HomepageFeatureRepository;
import com.personal.rebooked.utils.Constants;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.bson.types.ObjectId;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.*;

import static org.springframework.data.mongodb.core.query.Criteria.where;

/**
 * §24d featured this week: a category with 6 books, an author with up to 4, and 3 hero books.
 * Chosen once per week (Monday 06:00 Lagos) and kept in homepage_features; an admin can override any field and
 * the rest stay automatic. At serve time books are re-read and backfilled, but the stored week isn't rewritten.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class FeaturedSection {
    static final String KEY_PREFIX = "homepage:featured:";
    static final Duration TTL = Duration.ofDays(8);
    static final int BOOKS = 6;
    static final int MIN_BOOKS = 4;
    static final int HERO = 3;
    static final int AUTHOR_BOOKS = 4;
    static final int MIN_CATEGORY_BOOKS = 6;
    static final int CATEGORY_HISTORY_WEEKS = 8;
    static final int AUTHOR_HISTORY_WEEKS = 4;
    static final int CATEGORY_SHORTLIST = 3;
    static final int NOTE_MAX = 200;
    private static final int CATEGORY_POOL = 200;
    private static final int AUTHOR_CANDIDATES = 200;

    private final HomepageCache cache;
    private final HomepageFeatureRepository repository;
    private final HomepageBooks books;
    private final BookService bookService;
    private final CategoryService categoryService;
    private final CategoryRepository categoryRepository;
    private final AuthorService authorService;
    private final TopAuthorsSection topAuthors;
    private final TrendingSection trending;
    private final WeekViews weekViews;
    private final MongoTemplate mongoTemplate;
    private final Clock clock;
    private final Random random = new Random();

    public record Resolved(FeaturedView featured, List<Book> hero) {}

    public static String key(LocalDate weekStart) {
        return KEY_PREFIX + weekStart;
    }

    // ---------------------------------------------------------------- serving

    public Resolved read() {
        LocalDate week = HomepageWeek.current(clock);
        HomepageFeature feature = cache.get(key(week), new TypeReference<HomepageFeature>() {})
                .orElseGet(() -> ensure(week));
        return resolve(feature);
    }

    /** Re-reads every id, keeps ACTIVE books and backfills, without rewriting the stored week. */
    public Resolved resolve(HomepageFeature f) {
        Map<String, Double> views = null;

        Category category = null;
        if (f.getCategoryId() != null && ObjectId.isValid(f.getCategoryId())) {
            category = categoryRepository.findById(f.getCategoryId()).orElse(null);
            if (category != null) category.setBooksCount((int) countActiveIn(category.getId()));
        }

        List<Book> featuredBooks = books.active(f.getBookIds());
        if (featuredBooks.size() < MIN_BOOKS && category != null) {
            views = weekViews.scores();
            HomepageBooks.fill(featuredBooks, BOOKS, rankedCategoryBooks(category.getId(), views), b -> true);
        }

        FeaturedView.Author author = null;
        if (f.getAuthorId() != null) {
            Author a = authorService.findCanonical(f.getAuthorId()).orElse(null);
            if (a != null && a.getStatus() != Author.Status.MERGED) {
                author = new FeaturedView.Author(authorService.detail(a), authorBooks(a.getId()));
            }
        }

        List<Book> hero = books.active(f.getHeroBookIds());
        if (hero.size() < HERO) {
            Set<String> featuredIds = new HashSet<>(HomepageBooks.ids(featuredBooks));
            HomepageBooks.fill(hero, HERO, trending.read(), b -> HomepageBooks.hasCover(b) && !featuredIds.contains(b.getId()));
            Set<String> exclude = new HashSet<>(featuredIds);
            exclude.addAll(HomepageBooks.ids(hero));
            HomepageBooks.fill(hero, HERO, books.newest(HERO * 2, true, exclude), b -> true);
        }

        FeaturedView view = category == null && author == null && featuredBooks.isEmpty() ? null
                : new FeaturedView(category, featuredBooks, author, f.getSource());
        return new Resolved(view, hero);
    }

    // ---------------------------------------------------------------- building

    /** The week's set: the stored one, or a new AUTO pick. Cached either way. Used by the weekly job and on a miss. */
    public HomepageFeature ensure(LocalDate week) {
        HomepageFeature feature = repository.findByWeekStart(week).orElseGet(() -> generate(week, null));
        cache.put(key(week), feature, TTL);
        return feature;
    }

    public HomepageFeature ensureCurrent() {
        return ensure(HomepageWeek.current(clock));
    }

    /** POST /admin/homepage/refresh/featured: re-picks every field the admin didn't choose. */
    public HomepageFeature regenerateCurrent() {
        LocalDate week = HomepageWeek.current(clock);
        HomepageFeature feature = generate(week, repository.findByWeekStart(week).orElse(null));
        cache.put(key(week), feature, TTL);
        return feature;
    }

    /** Fills every field of {@code existing} (or a new week) that an admin didn't choose, and saves it. */
    HomepageFeature generate(LocalDate week, HomepageFeature existing) {
        HomepageFeature f = existing != null ? existing : new HomepageFeature();
        f.setWeekStart(week);
        Set<String> fixed = f.getAdminFields() == null ? Set.of() : f.getAdminFields();
        Map<String, Double> views = weekViews.scores();

        if (!fixed.contains(HomepageFeature.CATEGORY)) f.setCategoryId(chooseCategory(week, views));
        if (!fixed.contains(HomepageFeature.BOOKS)) {
            f.setBookIds(f.getCategoryId() == null ? new ArrayList<>()
                    : HomepageBooks.ids(rankedCategoryBooks(f.getCategoryId(), views).stream().limit(BOOKS).toList()));
        }
        if (!fixed.contains(HomepageFeature.AUTHOR)) f.setAuthorId(chooseAuthor(week));
        if (!fixed.contains(HomepageFeature.HERO)) f.setHeroBookIds(chooseHero(f.getBookIds()));
        f.setSource(fixed.isEmpty() ? FeatureSource.AUTO : FeatureSource.ADMIN);

        Instant now = clock.instant();
        if (f.getCreatedAt() == null) f.setCreatedAt(now);
        f.setUpdatedAt(now);
        try {
            return repository.save(f);
        } catch (DuplicateKeyException e) {
            // Another instance (or the warm-up) created the week first
            return repository.findByWeekStart(week).orElseThrow(() -> e);
        }
    }

    /** A seeded category with ≥ 6 ACTIVE books, not featured in the last 8 weeks, picked from the top 3 by view share. */
    private String chooseCategory(LocalDate week, Map<String, Double> views) {
        Map<String, Long> counts = categoryService.countActiveBooksByCategory();
        List<String> eligible = categoryRepository.findByIsSeededTrue().stream()
                .map(Category::getId)
                .filter(id -> counts.getOrDefault(id, 0L) >= MIN_CATEGORY_BOOKS)
                .toList();
        Map<String, LocalDate> lastFeatured = new HashMap<>();
        for (HomepageFeature past : history(week, CATEGORY_HISTORY_WEEKS)) {
            if (past.getCategoryId() != null) lastFeatured.merge(past.getCategoryId(), past.getWeekStart(),
                    (a, b) -> a.isAfter(b) ? a : b);
        }
        return pickCategory(eligible, lastFeatured, week.minusWeeks(CATEGORY_HISTORY_WEEKS), categoryViews(views), random);
    }

    /**
     * From the eligible categories not featured since {@code repeatCutoff}, the top 3 by views, one at random.
     * If every eligible category was featured recently, the one featured longest ago.
     */
    static String pickCategory(List<String> eligible, Map<String, LocalDate> lastFeatured, LocalDate repeatCutoff,
                               Map<String, Double> views, Random random) {
        if (eligible.isEmpty()) return null;
        List<String> fresh = eligible.stream()
                .filter(id -> !lastFeatured.containsKey(id) || lastFeatured.get(id).isBefore(repeatCutoff))
                .sorted(Comparator.<String>comparingDouble(id -> views.getOrDefault(id, 0d)).reversed()
                        .thenComparing(Comparator.naturalOrder()))
                .toList();
        if (!fresh.isEmpty()) {
            List<String> shortlist = fresh.subList(0, Math.min(CATEGORY_SHORTLIST, fresh.size()));
            return shortlist.get(random.nextInt(shortlist.size()));
        }
        return eligible.stream()
                .min(Comparator.comparing((String id) -> lastFeatured.getOrDefault(id, LocalDate.MIN))
                        .thenComparing(Comparator.naturalOrder()))
                .orElse(null);
    }

    /** The best-scoring author this week with a biography or photo who wasn't featured in the last 4 weeks. */
    private String chooseAuthor(LocalDate week) {
        Set<String> recent = new HashSet<>();
        for (HomepageFeature past : history(week, AUTHOR_HISTORY_WEEKS)) {
            if (past.getAuthorId() != null) recent.add(past.getAuthorId());
        }
        List<String> candidates = topAuthors.rankAll().stream().map(ScoredAuthor::authorId)
                .filter(id -> !recent.contains(id)).limit(AUTHOR_CANDIDATES).toList();
        if (candidates.isEmpty()) return null;
        Map<String, Author> byId = new HashMap<>();
        for (Author a : mongoTemplate.find(Query.query(where("_id").in(candidates)), Author.class)) byId.put(a.getId(), a);
        for (String id : candidates) {
            Author a = byId.get(id);
            if (a == null) continue;
            AuthorDetail detail = authorService.detail(a);
            if (hasText(detail.biography()) || hasText(detail.photoUrl())) return id;
        }
        return null;
    }

    /** The top trending books with a cover, not already featured; then the newest with a cover. */
    private List<String> chooseHero(List<String> featuredBookIds) {
        Set<String> featured = new HashSet<>(featuredBookIds == null ? List.of() : featuredBookIds);
        List<Book> hero = new ArrayList<>();
        HomepageBooks.fill(hero, HERO, trending.read(), b -> HomepageBooks.hasCover(b) && !featured.contains(b.getId()));
        Set<String> exclude = new HashSet<>(featured);
        exclude.addAll(HomepageBooks.ids(hero));
        HomepageBooks.fill(hero, HERO, books.newest(HERO * 2, true, exclude), b -> true);
        return new ArrayList<>(HomepageBooks.ids(hero));
    }

    /** The category's ACTIVE books: with a cover first, then by 7-day views, then newest. */
    private List<Book> rankedCategoryBooks(String categoryId, Map<String, Double> views) {
        Query query = Query.query(where("status").is(Constants.BookStatus.ACTIVE)
                        .and("categories.$id").is(new ObjectId(categoryId)))
                .with(Sort.by(Sort.Direction.DESC, "createdAt")).limit(CATEGORY_POOL);
        List<Book> pool = new ArrayList<>(mongoTemplate.find(query, Book.class));
        pool.sort(bookOrder(views));
        return pool;
    }

    static Comparator<Book> bookOrder(Map<String, Double> views) {
        return Comparator.comparing((Book b) -> !HomepageBooks.hasCover(b))
                .thenComparing(b -> views.getOrDefault(b.getId(), 0d), Comparator.reverseOrder())
                .thenComparing(b -> b.getCreatedAt() == null ? new Date(0) : b.getCreatedAt(), Comparator.reverseOrder());
    }

    private List<Book> authorBooks(String authorId) {
        Query query = Query.query(where("authors.authorId").is(authorId).and("status").is(Constants.BookStatus.ACTIVE))
                .with(Sort.by(Sort.Direction.DESC, "createdAt")).limit(AUTHOR_BOOKS * 5);
        List<Book> list = new ArrayList<>(mongoTemplate.find(query, Book.class));
        list.sort(Comparator.comparing((Book b) -> !HomepageBooks.hasCover(b))); // stable: newest within each group
        return new ArrayList<>(list.subList(0, Math.min(AUTHOR_BOOKS, list.size())));
    }

    /** Category id → share-free sum of 7-day views over its ACTIVE books (only the ranking matters). */
    private Map<String, Double> categoryViews(Map<String, Double> views) {
        Map<String, Double> byCategory = new HashMap<>();
        if (views.isEmpty()) return byCategory;
        List<String> ids = views.keySet().stream().filter(ObjectId::isValid).toList();
        Query query = Query.query(where("_id").in(ids).and("status").is(Constants.BookStatus.ACTIVE));
        query.fields().include("categories");
        for (Book b : mongoTemplate.find(query, Book.class)) {
            if (b.getCategories() == null) continue;
            double v = views.getOrDefault(b.getId(), 0d);
            for (Category c : b.getCategories()) if (c != null) byCategory.merge(c.getId(), v, Double::sum);
        }
        return byCategory;
    }

    private long countActiveIn(String categoryId) {
        return mongoTemplate.count(Query.query(where("status").is(Constants.BookStatus.ACTIVE)
                .and("categories.$id").is(new ObjectId(categoryId))), Book.class);
    }

    private List<HomepageFeature> history(LocalDate week, int weeks) {
        return repository.findWeeksBetween(week.minusWeeks(weeks), week);
    }

    // ---------------------------------------------------------------- admin

    public record AdminView(LocalDate week, HomepageFeature feature, FeaturedView featured, List<Book> hero,
                            List<HomepageFeature> history) {}

    /** GET /admin/homepage/featured?week= */
    public AdminView adminView(LocalDate week) {
        HomepageFeature feature = week.equals(HomepageWeek.current(clock)) ? ensure(week)
                : repository.findByWeekStart(week).orElse(null);
        Resolved resolved = feature == null ? new Resolved(null, List.of()) : resolve(feature);
        return new AdminView(week, feature, resolved.featured(), resolved.hero(), repository.findTop12ByOrderByWeekStartDesc());
    }

    /** PUT /admin/homepage/featured: overrides this week's set; omitted fields keep (or get) the AUTO choice. */
    public AdminView override(FeaturedOverride body, String adminId) {
        if (body == null) body = new FeaturedOverride(null, null, null, null, null);
        Map<String, String> errors = new LinkedHashMap<>();
        String categoryId = blankToNull(body.categoryId());
        if (categoryId != null && (!ObjectId.isValid(categoryId) || !categoryRepository.existsById(categoryId))) {
            errors.put("categoryId", "Category not found");
        }
        List<String> bookIds = cleanIds(body.bookIds());
        validateBooks(bookIds, BOOKS, "bookIds", errors);
        List<String> heroIds = cleanIds(body.heroBookIds());
        validateBooks(heroIds, HERO, "heroBookIds", errors);
        String authorId = blankToNull(body.authorId());
        if (authorId != null) {
            Optional<Author> author = ObjectId.isValid(authorId) ? authorService.findCanonical(authorId) : Optional.empty();
            if (author.isEmpty()) errors.put("authorId", "Author not found");
            else authorId = author.get().getId();
        }
        String note = blankToNull(body.note());
        if (note != null && note.length() > NOTE_MAX) errors.put("note", "Keep the note to " + NOTE_MAX + " characters");
        if (!errors.isEmpty()) throw new ApiException(HttpStatus.BAD_REQUEST, "Validation failed", errors);

        LocalDate week = HomepageWeek.current(clock);
        HomepageFeature f = repository.findByWeekStart(week).orElseGet(HomepageFeature::new);
        Set<String> fixed = new LinkedHashSet<>(f.getAdminFields() == null ? Set.of() : f.getAdminFields());
        if (categoryId != null) { f.setCategoryId(categoryId); fixed.add(HomepageFeature.CATEGORY); }
        if (!bookIds.isEmpty()) { f.setBookIds(bookIds); fixed.add(HomepageFeature.BOOKS); }
        if (authorId != null) { f.setAuthorId(authorId); fixed.add(HomepageFeature.AUTHOR); }
        if (!heroIds.isEmpty()) { f.setHeroBookIds(heroIds); fixed.add(HomepageFeature.HERO); }
        // A new category without its own books: the automatic books must follow it
        if (categoryId != null && bookIds.isEmpty()) fixed.remove(HomepageFeature.BOOKS);
        f.setAdminFields(fixed);
        f.setCreatedBy(adminId);
        if (note != null) f.setNote(note);

        cache.evict(key(week));
        HomepageFeature saved = generate(week, f);
        cache.put(key(week), saved, TTL);
        return adminView(week);
    }

    /** DELETE /admin/homepage/featured?week=: drops the override and picks the week automatically again. */
    public AdminView dropOverride(LocalDate week) {
        repository.findByWeekStart(week).ifPresent(repository::delete);
        cache.evict(key(week));
        HomepageFeature fresh = generate(week, null);
        cache.put(key(week), fresh, TTL);
        return adminView(week);
    }

    private void validateBooks(List<String> ids, int max, String field, Map<String, String> errors) {
        if (ids.isEmpty()) return;
        if (ids.size() > max) {
            errors.put(field, "At most " + max + " books");
            return;
        }
        List<Book> found = bookService.booksInOrder(ids);
        if (found.size() != ids.size()) errors.put(field, "One of these books doesn't exist");
        else if (!found.stream().allMatch(HomepageBooks::isActive)) errors.put(field, "Only published (ACTIVE) books can be featured");
    }

    private static List<String> cleanIds(List<String> ids) {
        if (ids == null) return List.of();
        return new ArrayList<>(new LinkedHashSet<>(ids.stream().filter(Objects::nonNull).map(String::trim)
                .filter(s -> !s.isEmpty()).toList()));
    }

    private static String blankToNull(String s) {
        return s == null || s.isBlank() ? null : s.trim();
    }

    private static boolean hasText(String s) {
        return s != null && !s.isBlank();
    }
}

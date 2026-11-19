package com.personal.rebooked.seed.demo;

import com.personal.rebooked.author.AuthorNames;
import com.personal.rebooked.author.AuthorStats;
import com.personal.rebooked.author.models.Author;
import com.personal.rebooked.author.models.AuthorExternalIdentifier;
import com.personal.rebooked.author.models.AuthorExternalIdentifier.IdSource;
import com.personal.rebooked.book.models.Book;
import com.personal.rebooked.book.models.BookAuthor;
import com.personal.rebooked.book.models.BookLocation;
import com.personal.rebooked.book.saved.SavedBook;
import com.personal.rebooked.book.views.BookViews;
import com.personal.rebooked.book.views.RecentViews;
import com.personal.rebooked.category.models.Category;
import com.personal.rebooked.file.models.File;
import com.personal.rebooked.homepage.HomepageService;
import com.personal.rebooked.homepage.WeekViews;
import com.personal.rebooked.rating.SellerRating;
import com.personal.rebooked.seed.demo.DemoFixture.Work;
import com.personal.rebooked.seed.demo.DemoFixture.WorkAuthor;
import com.personal.rebooked.user.models.Address;
import com.personal.rebooked.user.models.Profile;
import com.personal.rebooked.user.models.User;
import com.personal.rebooked.utils.Constants;
import com.personal.rebooked.utils.Constants.BookCondition;
import com.personal.rebooked.utils.Constants.BookStatus;
import com.personal.rebooked.utils.Misc;
import lombok.extern.slf4j.Slf4j;
import org.bson.Document;
import org.bson.types.ObjectId;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.core.annotation.Order;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.geo.GeoJsonPoint;
import org.springframework.data.redis.connection.StringRedisConnection;
import org.springframework.data.redis.core.RedisCallback;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.file.Path;
import java.time.Duration;
import java.time.LocalDate;
import java.util.*;

import static org.springframework.data.mongodb.core.query.Criteria.where;
import static org.springframework.data.mongodb.core.query.Query.query;

/**
 * Local demo data: about 5,000 real books (from the Open Library fixture) with sellers, readers, authors, sales,
 * seller ratings, saved books, recently viewed and a week of views, so the shop, search and homepage behave like
 * a live marketplace. Opt-in only:
 * <pre>
 *   ./gradlew bootRun --args='--seed.demo=true'          # seed (once; skipped when demo users exist)
 *   ./gradlew bootRun --args='--seed.demo.reset=true'    # remove everything it created
 * </pre>
 * Refuses a non-local Mongo unless seed.demo.allow-remote=true. Everything hangs off users with
 * {@code @demo.rebooked.test} emails (authors carry createdBy=seed:demo), which is how {@link DemoReset} finds it.
 * Documents are written raw so backdated createdAt survives (auditing would overwrite it).
 */
@Slf4j
@Component
@Order(100)
@ConditionalOnExpression("${seed.demo:false} or ${seed.demo.reset:false}")
public class DemoSeeder implements ApplicationRunner {
    public static final String EMAIL_DOMAIN = "demo.rebooked.test";
    public static final String EMAIL_REGEX = "@demo\\.rebooked\\.test$";
    public static final String CREATED_BY = "seed:demo";
    static final String PASSWORD = "Demo_1234!";
    static final String SELLER_EMAIL = "seller@" + EMAIL_DOMAIN;
    static final String READER_EMAIL = "reader@" + EMAIL_DOMAIN;
    static final String NEWBIE_EMAIL = "newbie@" + EMAIL_DOMAIN;
    static final String RARE = "Rare & Collectible Editions";
    static final String FALLBACK_CATEGORY = "Contemporary Fiction";
    static final int MIN_PRICE = 1500;
    static final int MAX_PRICE = 15000;
    static final int MAX_PREMIUM_PRICE = 40000;
    private static final long DAY = Duration.ofDays(1).toMillis();
    private static final int BATCH = 1000;
    private static final int WEEK_DAYS = 7;

    private final MongoTemplate mongoTemplate;
    private final StringRedisTemplate redis;
    private final PasswordEncoder passwordEncoder;
    private final AuthorStats authorStats;
    private final WeekViews weekViews;
    private final HomepageService homepageService;
    private final DemoReset demoReset;

    @Value("${seed.demo:false}")
    private boolean seed;
    @Value("${seed.demo.reset:false}")
    private boolean reset;
    @Value("${seed.demo.allow-remote:false}")
    private boolean allowRemote;
    @Value("${seed.demo.random-seed:42}")
    private long randomSeed;
    @Value("${seed.demo.books:5000}")
    private int bookCount;
    @Value("${seed.demo.fixture:seed/openlibrary-books.json.gz}")
    private String fixture;
    @Value("${spring.data.mongodb.uri:}")
    private String mongoUri;

    public DemoSeeder(MongoTemplate mongoTemplate, StringRedisTemplate redis, PasswordEncoder passwordEncoder,
                      AuthorStats authorStats, WeekViews weekViews, HomepageService homepageService, DemoReset demoReset) {
        this.mongoTemplate = mongoTemplate;
        this.redis = redis;
        this.passwordEncoder = passwordEncoder;
        this.authorStats = authorStats;
        this.weekViews = weekViews;
        this.homepageService = homepageService;
        this.demoReset = demoReset;
    }

    @Override
    public void run(ApplicationArguments args) {
        if (!allowRemote && !isLocal(mongoUri)) {
            log.error("Demo seeding refused: the Mongo URI doesn't point at a local database. "
                    + "Set seed.demo.allow-remote=true if you really mean it.");
            return;
        }
        try {
            if (reset) demoReset.reset();
            if (seed) seed();
        } catch (Exception e) {
            log.error("Demo seeding failed; run with --seed.demo.reset=true to clear partial data", e);
        }
    }

    // ---------------------------------------------------------------- generation state

    record Person(User user, Profile profile, boolean seller, List<String> favourites) {
        String id() {
            return user.getId();
        }
    }

    /** One listing being built: the book plus what the later steps need. */
    static final class Listing {
        final Book book;
        final File file;
        final Work work;
        final Person seller;
        final double weight;

        Listing(Book book, File file, Work work, Person seller, double weight) {
            this.book = book;
            this.file = file;
            this.work = work;
            this.seller = seller;
            this.weight = weight;
        }

        boolean active() {
            return book.getStatus() == BookStatus.ACTIVE;
        }
    }

    // ---------------------------------------------------------------- seeding

    void seed() throws Exception {
        long existing = mongoTemplate.count(query(where("email").regex(EMAIL_REGEX)), User.class);
        if (existing > 0) {
            long books = mongoTemplate.count(query(where("user.$id").in(demoUserIds())), Book.class);
            log.info("Demo data already seeded ({} users, {} books). Run with --seed.demo.reset=true first to reseed.",
                    existing, books);
            return;
        }
        long started = System.currentTimeMillis();
        long now = started;
        Random random = new Random(randomSeed);

        List<Work> works = DemoFixture.read(Path.of(fixture));
        Map<String, Category> categories = new HashMap<>();
        mongoTemplate.findAll(Category.class).forEach(c -> categories.put(c.getName(), c));
        if (categories.isEmpty()) throw new IllegalStateException("No categories: the startup Seeder seeds them first");
        log.info("Demo seeding {} books from {} works...", bookCount, works.size());

        double[] weights = popularity(works);
        List<Integer> chosen = chooseListings(weights, bookCount, random);

        String passwordHash = passwordEncoder.encode(PASSWORD);
        List<String> categoryNames = new ArrayList<>(categories.keySet());
        Collections.sort(categoryNames);
        List<Person> sellers = people(Math.max(20, bookCount * 11 / 100), true, passwordHash, categoryNames, now, random);
        List<Person> readers = people(Math.max(30, bookCount * 13 / 100), false, passwordHash, categoryNames, now, random);

        Map<String, Author> authors = new LinkedHashMap<>();
        List<Author> newAuthors = new ArrayList<>();
        List<AuthorExternalIdentifier> identifiers = new ArrayList<>();
        resolveAuthors(chosen.stream().map(works::get).toList(), authors, newAuthors, identifiers, now, random);

        List<Listing> listings = listings(chosen, works, weights, sellers, authors, categories, now, random);
        List<Person> everyone = new ArrayList<>(sellers);
        everyone.addAll(readers);
        sell(listings, readers, everyone, now, random);
        Map<String, Map<String, Integer>> dayViews = views(listings, now, random);
        List<SellerRating> ratings = ratings(listings, now, random);
        List<SavedBook> saved = new ArrayList<>();
        List<RecentViews> recents = new ArrayList<>();
        readerActivity(everyone, listings, saved, recents, now, random);

        // Write: everything is built, so a failure above leaves the database untouched
        Map<String, Document> summaries = ratingSummaries(ratings);
        List<Document> profiles = new ArrayList<>();
        for (Person p : everyone) {
            Document doc = toDocument(p.profile());
            Document summary = summaries.get(p.id());
            if (summary != null) doc.put("sellerRating", summary);
            profiles.add(doc);
        }
        insertDocuments(Profile.class, profiles);
        insert(User.class, everyone.stream().map(Person::user).toList());
        insert(Author.class, newAuthors);
        insert(AuthorExternalIdentifier.class, identifiers);
        insert(File.class, listings.stream().map(l -> l.file).toList());
        insert(Book.class, listings.stream().map(l -> l.book).toList());
        insert(SellerRating.class, ratings);
        insert(SavedBook.class, saved);
        insert(RecentViews.class, recents);
        writeDayViews(dayViews);

        weekViews.rebuild();
        authorStats.recount(authors.values().stream().map(Author::getId).toList());
        Map<String, Object> homepage = homepageService.refresh("all");

        Map<BookStatus, Long> byStatus = new EnumMap<>(BookStatus.class);
        listings.forEach(l -> byStatus.merge(l.book.getStatus(), 1L, Long::sum));
        log.info("""
                        Demo data seeded in {} s:
                          users {} ({} sellers, {} readers), authors {} new + {} reused, books {} {}
                          seller ratings {}, saved books {}, recently viewed lists {}
                          homepage refresh {}
                          Logins (password {}): {} (big shop), {} (history, for-you), {} (no history)""",
                (System.currentTimeMillis() - started) / 1000, everyone.size(), sellers.size(), readers.size(),
                newAuthors.size(), authors.size() - newAuthors.size(), listings.size(), byStatus, ratings.size(),
                saved.size(), recents.size(), homepage, PASSWORD, SELLER_EMAIL, READER_EMAIL, NEWBIE_EMAIL);
    }

    private List<ObjectId> demoUserIds() {
        return mongoTemplate.getCollection(mongoTemplate.getCollectionName(User.class))
                .find(new Document("email", new Document("$regex", EMAIL_REGEX)))
                .projection(new Document("_id", 1))
                .map(d -> d.getObjectId("_id")).into(new ArrayList<>());
    }

    /**
     * How popular each work is, max 1: falls with its rank inside its main category (the fixture keeps Open Library's
     * relevance order), boosted for Nigerian and African literature.
     */
    static double[] popularity(List<Work> works) {
        Map<String, Integer> seen = new HashMap<>();
        double[] weights = new double[works.size()];
        double max = 0;
        for (int i = 0; i < works.size(); i++) {
            Work w = works.get(i);
            int rank = seen.merge(String.valueOf(w.primaryCategory()), 1, Integer::sum) - 1;
            weights[i] = Math.pow(rank + 1, -0.7) * (w.african() ? 2.5 : 1.0);
            max = Math.max(max, weights[i]);
        }
        for (int i = 0; i < weights.length; i++) weights[i] /= max;
        return weights;
    }

    /**
     * Work indexes for {@code total} listings: the most popular ~75% of the budget once each, the rest as extra copies
     * drawn by popularity (several sellers listing the same bestseller, as on a real resale site).
     */
    static List<Integer> chooseListings(double[] weights, int total, Random random) {
        Integer[] byWeight = new Integer[weights.length];
        for (int i = 0; i < weights.length; i++) byWeight[i] = i;
        Arrays.sort(byWeight, Comparator.<Integer>comparingDouble(i -> weights[i]).reversed().thenComparing(i -> i));
        int unique = Math.min(weights.length, (int) Math.round(total * 0.75));
        List<Integer> chosen = new ArrayList<>(Arrays.asList(byWeight).subList(0, unique));
        if (chosen.isEmpty()) return chosen;
        Sampler extra = Sampler.of(chosen, i -> weights[i]);
        while (chosen.size() < total) chosen.add(extra.next(random));
        Collections.shuffle(chosen, random);
        return chosen;
    }

    private List<Person> people(int count, boolean sellers, String passwordHash, List<String> categoryNames, long now,
                                Random random) {
        List<Person> people = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            String first = DemoPeople.pick(DemoPeople.FIRST_NAMES, random);
            String last = DemoPeople.pick(DemoPeople.LAST_NAMES, random);
            String email = (first + "." + last + "." + (sellers ? "s" : "r") + i).toLowerCase(Locale.ROOT) + "@" + EMAIL_DOMAIN;
            boolean fixed = false;
            if (sellers && i == 0) {
                first = "Kunle";
                last = "Adeyemi";
                email = SELLER_EMAIL;
                fixed = true;
            } else if (!sellers && i == 0) {
                first = "Ada";
                last = "Okafor";
                email = READER_EMAIL;
                fixed = true;
            } else if (!sellers && i == 1) {
                first = "Tobi";
                last = "Bello";
                email = NEWBIE_EMAIL;
                fixed = true;
            }

            long joined = sellers ? now - (200 + random.nextInt(200)) * DAY : now - (1 + random.nextInt(365)) * DAY;
            Date joinedAt = new Date(joined - random.nextInt((int) Duration.ofHours(20).toMillis()));

            Profile profile = new Profile();
            profile.setId(new ObjectId().toHexString());
            boolean shop = sellers && i < 40;
            profile.setUserName(shop ? first + "'s " + DemoPeople.pick(DemoPeople.SHOP_SUFFIXES, random)
                    : first + " " + last.charAt(0) + ".");
            if (sellers) {
                profile.setBio(DemoPeople.pick(DemoPeople.SELLER_BIOS, random));
                if (random.nextDouble() < 0.6) {
                    profile.setContactPhone(Misc.normalizeNigerianPhone("080" + (10_000_000 + random.nextInt(90_000_000))));
                }
            }
            if (sellers || fixed || random.nextDouble() < 0.85) profile.setAddress(address(random));
            profile.setOnboardingCompleted(profile.getAddress() != null);
            profile.setCreatedAt(joinedAt);
            profile.setUpdatedAt(joinedAt);

            User user = new User();
            user.setId(new ObjectId().toHexString());
            user.setEmail(email);
            user.setPassword(passwordHash);
            user.setFullName(first + " " + last);
            user.setRegistrationType(Constants.RegistrationType.EMAIL);
            user.setRole(Constants.ROLE_USER);
            user.setProfile(profile);
            user.setEmailVerified(sellers || fixed || random.nextDouble() < 0.93);
            user.setDeleted(false);
            user.setCreatedAt(joinedAt);
            user.setUpdatedAt(joinedAt);

            List<String> favourites = new ArrayList<>();
            int wanted = 1 + random.nextInt(3);
            while (favourites.size() < wanted) {
                String c = DemoPeople.pick(categoryNames, random);
                if (!c.equals(RARE) && !favourites.contains(c)) favourites.add(c);
            }
            if (email.equals(READER_EMAIL)) favourites = List.of("Contemporary Fiction", "Mystery & Thriller", "Poetry");
            people.add(new Person(user, profile, sellers, favourites));
        }
        return people;
    }

    private static Address address(Random random) {
        DemoPeople.State state = DemoPeople.pickState(random);
        DemoPeople.City city = DemoPeople.pick(state.cities(), random);
        Address a = new Address();
        a.setCountry(Constants.DEFAULT_COUNTRY);
        a.setState(state.name());
        a.setCity(city.name());
        a.setFormattedAddress(city.name() + ", " + state.name() + ", Nigeria");
        a.setLocation(new GeoJsonPoint(city.lng() + (random.nextDouble() - 0.5) * 0.06,
                city.lat() + (random.nextDouble() - 0.5) * 0.06));
        return a;
    }

    /**
     * One author per distinct normalised name: an existing (non-merged) author is reused, otherwise a new IMPORTED one
     * with search keys, a unique slug and its Open Library id (also as an external identifier, so a later Open Library
     * import links to it instead of creating a duplicate).
     */
    private void resolveAuthors(List<Work> works, Map<String, Author> authors, List<Author> newAuthors,
                                List<AuthorExternalIdentifier> identifiers, long now, Random random) {
        Map<String, WorkAuthor> wanted = new LinkedHashMap<>();
        Map<String, List<Work>> worksBy = new HashMap<>();
        Set<String> countedWorks = new HashSet<>();
        for (Work w : works) {
            boolean firstTime = countedWorks.add(w.workKey());
            for (WorkAuthor a : w.authors()) {
                String norm = AuthorNames.normalize(a.name());
                if (norm == null || norm.isBlank()) continue;
                wanted.putIfAbsent(norm, a);
                if (firstTime) worksBy.computeIfAbsent(norm, k -> new ArrayList<>()).add(w);
            }
        }
        if (wanted.isEmpty()) return;

        for (Author a : mongoTemplate.find(query(where("normalizedName").in(wanted.keySet())
                .and("status").ne(Author.Status.MERGED)), Author.class)) {
            authors.putIfAbsent(a.getNormalizedName(), a);
        }
        Set<String> slugs = new HashSet<>(mongoTemplate.getCollection(mongoTemplate.getCollectionName(Author.class))
                .distinct("slug", String.class).into(new ArrayList<>()));
        Set<String> olIds = new HashSet<>();
        for (Document d : mongoTemplate.getCollection(mongoTemplate.getCollectionName(AuthorExternalIdentifier.class))
                .find(new Document("source", IdSource.OPEN_LIBRARY.name())).projection(new Document("sourceId", 1))) {
            olIds.add(d.getString("sourceId"));
        }

        wanted.forEach((norm, source) -> {
            if (authors.containsKey(norm)) return;
            String name = AuthorNames.clean(source.name());
            String olId = IdSource.OPEN_LIBRARY.normalize(source.olKey());
            Date created = new Date(now - (long) (random.nextDouble() * 200 * DAY));
            Author a = new Author();
            a.setId(new ObjectId().toHexString());
            a.setName(name);
            a.setNormalizedName(norm);
            a.setSlug(uniqueSlug(Misc.slugify(name, "author"), slugs));
            a.setSearchKeys(AuthorNames.searchKeys(name, List.of()));
            a.setOrigin(Author.Origin.IMPORTED);
            a.setStatus(random.nextDouble() < 0.4 ? Author.Status.VERIFIED : Author.Status.UNVERIFIED);
            List<Work> theirs = worksBy.getOrDefault(norm, List.of());
            a.setWorkCount(Math.min(127, Math.max(1, theirs.size())));
            a.getFieldSources().put("name", Author.Source.OPEN_LIBRARY);
            // Our own one-line text (not Open Library's), so the featured-author pick, which needs a biography or a
            // photo, has candidates
            String biography = biography(name, theirs);
            if (biography != null) {
                a.setBiography(biography);
                a.setDescription("Author of " + theirs.get(0).title());
                a.getFieldSources().put("biography", Author.Source.ADMIN);
                a.getFieldSources().put("description", Author.Source.ADMIN);
            }
            if (olId != null) a.setLinks(new Author.Links(IdSource.OPEN_LIBRARY.url(olId), null, null));
            a.setCreatedBy(CREATED_BY);
            a.setCreatedAt(created);
            a.setUpdatedAt(created);
            authors.put(norm, a);
            newAuthors.add(a);
            if (olId != null && olIds.add(olId)) {
                AuthorExternalIdentifier id = new AuthorExternalIdentifier(a.getId(), IdSource.OPEN_LIBRARY, olId,
                        Author.Source.OPEN_LIBRARY);
                id.setId(new ObjectId().toHexString());
                id.setCreatedAt(created);
                identifiers.add(id);
            }
        });
    }

    /** "X is the author of A, B and C, first published as early as 1958." Null without works. */
    static String biography(String name, List<Work> works) {
        List<String> titles = works.stream().map(Work::title).distinct().limit(3).toList();
        if (titles.isEmpty()) return null;
        String list = titles.size() == 1 ? titles.get(0)
                : String.join(", ", titles.subList(0, titles.size() - 1)) + " and " + titles.get(titles.size() - 1);
        OptionalInt earliest = works.stream().map(Work::year).filter(Objects::nonNull).mapToInt(Integer::intValue).min();
        return name + " is the author of " + list
                + (earliest.isPresent() ? ", with work first published as early as " + earliest.getAsInt() : "") + ".";
    }

    /** base, base-2, base-3… (AuthorService.uniqueSlug's scheme), checked against {@code taken}, which it adds to. */
    static String uniqueSlug(String base, Set<String> taken) {
        if (taken.add(base)) return base;
        for (int n = 2; ; n++) {
            String slug = base + "-" + n;
            if (taken.add(slug)) return slug;
        }
    }

    private List<Listing> listings(List<Integer> chosen, List<Work> works, double[] weights, List<Person> sellers,
                                   Map<String, Author> authors, Map<String, Category> categories, long now, Random random) {
        Set<String> bookSlugs = new HashSet<>(mongoTemplate.getCollection(mongoTemplate.getCollectionName(Book.class))
                .distinct("slug", String.class).into(new ArrayList<>()));
        List<Integer> sellerIndexes = new ArrayList<>();
        for (int i = 0; i < sellers.size(); i++) sellerIndexes.add(i);
        // A few big shops and a long tail: seller i is picked in proportion to (i + 1)^-0.6
        Sampler sellerSampler = Sampler.of(sellerIndexes, i -> Math.pow(i + 1, -0.6));
        Set<String> sellerWork = new HashSet<>();

        List<Listing> listings = new ArrayList<>();
        for (int n = 0; n < chosen.size(); n++) {
            int wi = chosen.get(n);
            Work work = works.get(wi);
            Person seller = sellers.get(n < sellers.size() ? n : sellerSampler.next(random));
            for (int tries = 0; tries < 5 && !sellerWork.add(seller.id() + work.workKey()); tries++) {
                seller = sellers.get(sellerSampler.next(random));
            }

            long age = (long) (180 * DAY * Math.pow(random.nextDouble(), 1.5)) + random.nextInt((int) Duration.ofHours(1).toMillis());
            Date createdAt = new Date(now - age);
            BookCondition condition = condition(random);
            BookStatus status = status(random);

            List<String> categoryNames = new ArrayList<>();
            for (String c : work.categories() == null ? List.<String>of() : work.categories()) {
                if (categories.containsKey(c) && !categoryNames.contains(c)) categoryNames.add(c);
            }
            if (categoryNames.isEmpty()) categoryNames.add(FALLBACK_CATEGORY);
            boolean rare = "Classic Literature".equals(categoryNames.get(0)) && work.year() != null
                    && work.year() < 1900 && random.nextDouble() < 0.25 && categories.containsKey(RARE);
            if (rare) {
                if (categoryNames.size() >= 3) categoryNames.remove(categoryNames.size() - 1);
                categoryNames.add(RARE);
            }
            boolean free = !rare && random.nextDouble() < 0.03;

            File file = new File();
            file.setId(new ObjectId().toHexString());
            file.setUser(seller.user());
            file.setName(Misc.slugify(work.title()) + ".jpg");
            file.setUrl(work.coverUrl());
            file.setMimetype("image/jpeg");
            file.setCreatedAt(createdAt);
            file.setUpdatedAt(createdAt);

            Book book = new Book();
            book.setId(new ObjectId().toHexString());
            book.setUser(seller.user());
            book.setTitle(work.title());
            book.setSlug(bookSlug(work.title(), bookSlugs, random));
            Address address = seller.profile().getAddress();
            if (address != null) book.setLocation(new BookLocation(address.getState(), address.getCity()));
            book.setAuthors(bookAuthors(work, authors));
            book.setFree(free);
            book.setPrice(free ? 0.0 : price(work.pages(), categoryNames.get(0), condition, rare, random));
            book.setCondition(condition);
            book.setIsbn(work.isbn());
            book.setDescription(description(work, condition, random));
            book.setCoverImage(file);
            book.setImages(List.of(file));
            book.setCategories(categoryNames.stream().map(categories::get).toList());
            book.setTags(List.of());
            book.setStatus(status);
            book.setViewCount(0);
            book.setCreatedAt(createdAt);
            book.setUpdatedAt(createdAt);
            listings.add(new Listing(book, file, work, seller, weights[wi] * (0.5 + random.nextDouble())));
        }
        return listings;
    }

    private static List<BookAuthor> bookAuthors(Work work, Map<String, Author> authors) {
        List<BookAuthor> result = new ArrayList<>();
        Set<String> ids = new HashSet<>();
        for (WorkAuthor wa : work.authors()) {
            Author a = authors.get(AuthorNames.normalize(wa.name()));
            if (a != null && ids.add(a.getId())) result.add(new BookAuthor(a.getId(), result.size(), a.getName(), a.getSlug()));
        }
        return result;
    }

    /** slugify(title)-NNNN like BookService.uniqueSlug, unique against {@code taken}; 6 digits as a last resort. */
    static String bookSlug(String title, Set<String> taken, Random random) {
        String base = Misc.slugify(title);
        for (int i = 0; i < 20; i++) {
            String slug = base + "-" + (1000 + random.nextInt(9000));
            if (taken.add(slug)) return slug;
        }
        while (true) {
            String slug = base + "-" + (100000 + random.nextInt(900000));
            if (taken.add(slug)) return slug;
        }
    }

    static BookCondition condition(Random random) {
        double r = random.nextDouble();
        return r < 0.15 ? BookCondition.NEW : r < 0.50 ? BookCondition.LIKE_NEW : r < 0.85 ? BookCondition.GOOD : BookCondition.FAIR;
    }

    static BookStatus status(Random random) {
        double r = random.nextDouble();
        return r < 0.82 ? BookStatus.ACTIVE : r < 0.92 ? BookStatus.SOLD : r < 0.97 ? BookStatus.INACTIVE : BookStatus.DRAFT;
    }

    /** Naira, in steps of 500: longer books, better condition, textbooks and rare editions cost more. */
    static double price(Integer pages, String category, BookCondition condition, boolean rare, Random random) {
        int p = pages == null || pages <= 0 ? 280 : Math.min(pages, 1500);
        double base = 1800 + p * 7.5;
        double byCategory = switch (category == null ? "" : category) {
            case "Textbooks", "Reference Books" -> 1.8;
            case "Study Guides", "Art & Design", "Photography" -> 1.3;
            case "Picture Books", "Middle Grade Fiction" -> 0.7;
            default -> 1.0;
        };
        double byCondition = switch (condition) {
            case NEW -> 1.4;
            case LIKE_NEW -> 1.15;
            case GOOD -> 1.0;
            case FAIR -> 0.7;
        };
        double value = base * byCategory * byCondition * (0.8 + random.nextDouble() * 0.45) * (rare ? 3 : 1);
        boolean premium = rare || "Textbooks".equals(category) || "Reference Books".equals(category);
        long rounded = Math.round(value / 500) * 500;
        return Math.max(MIN_PRICE, Math.min(premium ? MAX_PREMIUM_PRICE : MAX_PRICE, rounded));
    }

    static String description(Work work, BookCondition condition, Random random) {
        StringBuilder s = new StringBuilder();
        if (work.year() != null) s.append("First published in ").append(work.year()).append(". ");
        if (work.pages() != null && work.pages() > 0) s.append(work.pages()).append(" pages. ");
        if (work.subjects() != null && !work.subjects().isEmpty()) {
            s.append("About ").append(String.join(", ", work.subjects().subList(0, Math.min(3, work.subjects().size()))))
                    .append(". ");
        }
        s.append(DemoPeople.pick(switch (condition) {
            case NEW -> DemoPeople.CONDITION_NOTES_NEW;
            case LIKE_NEW -> DemoPeople.CONDITION_NOTES_LIKE_NEW;
            case GOOD -> DemoPeople.CONDITION_NOTES_GOOD;
            case FAIR -> DemoPeople.CONDITION_NOTES_FAIR;
        }, random));
        return s.toString().trim();
    }

    /** SOLD listings get a buyer and a sale date; about a third sold in the last 7 days. The reader buys a few. */
    private static void sell(List<Listing> listings, List<Person> readers, List<Person> everyone, long now, Random random) {
        Person reader = readers.get(0);
        Person newbie = readers.size() > 1 ? readers.get(1) : null;
        int readerPurchases = 0;
        for (Listing l : listings) {
            if (l.book.getStatus() != BookStatus.SOLD) continue;
            Person buyer;
            if (readerPurchases < 6) {
                buyer = reader;
                readerPurchases++;
            } else {
                do {
                    buyer = everyone.get(random.nextInt(everyone.size()));
                } while (buyer == l.seller || buyer == newbie);
            }
            long created = l.book.getCreatedAt().getTime();
            long window = Math.max(Duration.ofHours(1).toMillis(), now - created);
            long soldAt = random.nextDouble() < 0.35
                    ? now - (long) (random.nextDouble() * Math.min(7 * DAY, window))
                    : created + (long) (random.nextDouble() * window);
            soldAt = Math.min(now, Math.max(created + 60_000, soldAt));
            l.book.setSoldDate(new Date(soldAt));
            l.book.setBuyerId(buyer.id());
            l.book.setUpdatedAt(new Date(soldAt));
        }
    }

    /**
     * Views of each ACTIVE book on each of the last 7 Lagos days (the Redis day buckets behind trending, top authors
     * and featured), and an all-time viewCount on every book that includes them.
     */
    private static Map<String, Map<String, Integer>> views(List<Listing> listings, long now, Random random) {
        Map<String, Map<String, Integer>> byDay = new LinkedHashMap<>();
        LocalDate today = LocalDate.now(BookViews.ZONE);
        for (Listing l : listings) {
            long created = l.book.getCreatedAt().getTime();
            int week = 0;
            if (l.active()) {
                for (int d = 0; d < WEEK_DAYS; d++) {
                    LocalDate day = today.minusDays(d);
                    long dayEnd = day.plusDays(1).atStartOfDay(BookViews.ZONE).toInstant().toEpochMilli();
                    if (created >= dayEnd) continue;
                    double expected = 40 * l.weight * (d == 0 ? 0.6 : 1.0);
                    int count = (int) Math.floor(expected * (0.5 + random.nextDouble()));
                    if (random.nextDouble() < expected - Math.floor(expected)) count++;
                    if (count <= 0) continue;
                    byDay.computeIfAbsent(BookViews.dayKey(day), k -> new HashMap<>()).put(l.book.getId(), count);
                    week += count;
                }
            }
            double ageDays = (now - created) / (double) DAY;
            long history = Math.round(1500 * l.weight * Math.min(1, ageDays / 90) * (0.6 + random.nextDouble() * 0.8));
            l.book.setViewCount((int) (history + week));
        }
        return byDay;
    }

    /** About 70% of sales rated by the buyer, mostly 4–5 stars, usually with a comment, 1–5 days after the sale. */
    private static List<SellerRating> ratings(List<Listing> listings, long now, Random random) {
        List<SellerRating> ratings = new ArrayList<>();
        for (Listing l : listings) {
            if (l.book.getStatus() != BookStatus.SOLD || random.nextDouble() >= 0.7) continue;
            double r = random.nextDouble();
            int stars = r < 0.55 ? 5 : r < 0.85 ? 4 : r < 0.94 ? 3 : r < 0.98 ? 2 : 1;
            long soldAt = l.book.getSoldDate().getTime();
            long at = soldAt + DAY + (long) (random.nextDouble() * 4 * DAY);
            if (at > now) at = soldAt + (long) (random.nextDouble() * (now - soldAt));
            SellerRating rating = new SellerRating();
            rating.setId(new ObjectId().toHexString());
            rating.setSellerId(l.seller.id());
            rating.setBuyerId(l.book.getBuyerId());
            rating.setBookId(l.book.getId());
            rating.setStars(stars);
            if (random.nextDouble() < 0.8) rating.setComment(DemoPeople.comment(stars, random));
            rating.setCreatedAt(new Date(at));
            rating.setUpdatedAt(new Date(at));
            ratings.add(rating);
        }
        return ratings;
    }

    /**
     * Saved books and recently viewed, drawn mostly from each reader's favourite categories, so the AI "for you" picks
     * have a taste to work from. The newbie has neither.
     */
    private static void readerActivity(List<Person> everyone, List<Listing> listings, List<SavedBook> saved,
                                       List<RecentViews> recents, long now, Random random) {
        List<Integer> active = new ArrayList<>();
        Map<String, List<Integer>> activeByCategory = new HashMap<>();
        for (int i = 0; i < listings.size(); i++) {
            Listing l = listings.get(i);
            if (!l.active()) continue;
            active.add(i);
            for (Category c : l.book.getCategories()) activeByCategory.computeIfAbsent(c.getName(), k -> new ArrayList<>()).add(i);
        }
        if (active.isEmpty()) return;
        Sampler all = Sampler.of(active, i -> listings.get(i).weight);
        Map<String, Sampler> byCategory = new HashMap<>();
        activeByCategory.forEach((c, ids) -> byCategory.put(c, Sampler.of(ids, i -> listings.get(i).weight)));

        int recentLists = 0;
        for (Person p : everyone) {
            String email = p.user().getEmail();
            if (email.equals(NEWBIE_EMAIL)) continue;
            boolean reader = email.equals(READER_EMAIL);
            List<Sampler> taste = p.favourites().stream().map(byCategory::get).filter(Objects::nonNull).toList();

            if (reader || random.nextDouble() < (p.seller() ? 0.3 : 0.75)) {
                int count = reader ? 25 : 1 + (int) (39 * Math.pow(random.nextDouble(), 2));
                Set<String> picked = new HashSet<>();
                for (int tries = 0; picked.size() < count && tries < count * 4; tries++) {
                    Listing l = listings.get(draw(taste, all, random));
                    if (l.seller == p || !picked.add(l.book.getId())) continue;
                    long from = Math.max(l.book.getCreatedAt().getTime(), now - 90 * DAY);
                    saved.add(new SavedBook(null, p.id(), l.book.getId(), new Date(from + (long) (random.nextDouble() * (now - from)))));
                }
            }

            if (reader || (recentLists < 300 && random.nextDouble() < 0.3)) {
                int count = reader ? BookViews.RECENT_MAX : 8 + random.nextInt(BookViews.RECENT_MAX - 7);
                Map<String, Long> viewed = new LinkedHashMap<>();
                for (int tries = 0; viewed.size() < count && tries < count * 4; tries++) {
                    Listing l = listings.get(draw(taste, all, random));
                    if (l.seller == p || viewed.containsKey(l.book.getId())) continue;
                    long from = Math.max(l.book.getCreatedAt().getTime(), now - 14 * DAY);
                    viewed.put(l.book.getId(), from + (long) (random.nextDouble() * (now - from)));
                }
                List<RecentViews.Item> items = viewed.entrySet().stream()
                        .sorted(Map.Entry.<String, Long>comparingByValue().reversed())
                        .map(e -> new RecentViews.Item(e.getKey(), e.getValue())).toList();
                if (!items.isEmpty()) {
                    recents.add(new RecentViews(p.id(), items, new Date(items.get(0).viewedAt())));
                    recentLists++;
                }
            }
        }
    }

    /** 70% from one of the person's favourite categories, otherwise anything ACTIVE. */
    private static int draw(List<Sampler> taste, Sampler all, Random random) {
        if (!taste.isEmpty() && random.nextDouble() < 0.7) return taste.get(random.nextInt(taste.size())).next(random);
        return all.next(random);
    }

    /** Profile.sellerRating per seller, the shape RatingService maintains incrementally. */
    static Map<String, Document> ratingSummaries(List<SellerRating> ratings) {
        Map<String, List<Integer>> stars = new HashMap<>();
        for (SellerRating r : ratings) stars.computeIfAbsent(r.getSellerId(), k -> new ArrayList<>()).add(r.getStars());
        Map<String, Document> summaries = new HashMap<>();
        stars.forEach((seller, list) -> summaries.put(seller, ratingSummary(list)));
        return summaries;
    }

    /** average = round(total / count, 1) half-even on the double, as Mongo's $round does in RatingService. */
    static Document ratingSummary(List<Integer> stars) {
        int total = stars.stream().mapToInt(Integer::intValue).sum();
        Document byStar = new Document();
        for (int s = 1; s <= 5; s++) {
            int star = s;
            byStar.put(String.valueOf(s), (int) stars.stream().filter(x -> x == star).count());
        }
        double average = stars.isEmpty() ? 0
                : new BigDecimal(total / (double) stars.size()).setScale(1, RoundingMode.HALF_EVEN).doubleValue();
        return new Document("average", average).append("count", stars.size()).append("total", total).append("stars", byStar);
    }

    // ---------------------------------------------------------------- writing

    private Document toDocument(Object entity) {
        Document doc = new Document();
        mongoTemplate.getConverter().write(entity, doc);
        return doc;
    }

    /** Converted with the mapping (DBRefs, enums, _class) but inserted raw: no auditing, no listeners. */
    private <T> void insert(Class<T> type, List<T> entities) {
        insertDocuments(type, entities.stream().map(this::toDocument).toList());
    }

    private void insertDocuments(Class<?> type, List<Document> docs) {
        var collection = mongoTemplate.getCollection(mongoTemplate.getCollectionName(type));
        for (int from = 0; from < docs.size(); from += BATCH) {
            collection.insertMany(docs.subList(from, Math.min(from + BATCH, docs.size())));
        }
    }

    /** ZINCRBY into the day buckets, which expire 8 days after their day like the ones BookViews writes. */
    private void writeDayViews(Map<String, Map<String, Integer>> byDay) {
        redis.executePipelined((RedisCallback<Object>) connection -> {
            StringRedisConnection c = (StringRedisConnection) connection;
            byDay.forEach((key, counts) -> counts.forEach((bookId, n) -> c.zIncrBy(key, n, bookId)));
            return null;
        });
        LocalDate today = LocalDate.now(BookViews.ZONE);
        for (int d = 0; d < WEEK_DAYS; d++) {
            String key = BookViews.dayKey(today.minusDays(d));
            Long ttl = redis.getExpire(key);
            if (ttl != null && ttl < 0 && Boolean.TRUE.equals(redis.hasKey(key))) {
                redis.expire(key, BookViews.DAY_BUCKET_TTL.minusDays(d));
            }
        }
    }

    // ---------------------------------------------------------------- guards and helpers

    /**
     * True when every host in a mongodb:// URI is this machine or a docker-compose service name (no dots).
     * mongodb+srv:// is always remote (Atlas).
     */
    static boolean isLocal(String uri) {
        if (uri == null || uri.isBlank()) return false;
        String u = uri.trim();
        if (!u.startsWith("mongodb://")) return false;
        String hosts = u.substring("mongodb://".length());
        int slash = hosts.indexOf('/');
        if (slash >= 0) hosts = hosts.substring(0, slash);
        int q = hosts.indexOf('?');
        if (q >= 0) hosts = hosts.substring(0, q);
        int at = hosts.lastIndexOf('@');
        if (at >= 0) hosts = hosts.substring(at + 1);
        if (hosts.isBlank()) return false;
        for (String hostPort : hosts.split(",")) {
            String host;
            if (hostPort.startsWith("[")) {
                int end = hostPort.indexOf(']');
                host = end > 0 ? hostPort.substring(1, end) : hostPort;
            } else {
                host = hostPort.split(":")[0];
            }
            host = host.toLowerCase(Locale.ROOT);
            boolean local = host.equals("localhost") || host.equals("127.0.0.1") || host.equals("::1")
                    || host.equals("host.docker.internal") || (!host.isEmpty() && !host.contains(".") && !host.contains(":"));
            if (!local) return false;
        }
        return true;
    }

    /** Draws items with probability proportional to their weight. */
    static final class Sampler {
        private final int[] items;
        private final double[] cumulative;

        private Sampler(int[] items, double[] cumulative) {
            this.items = items;
            this.cumulative = cumulative;
        }

        static Sampler of(List<Integer> items, java.util.function.IntToDoubleFunction weight) {
            int[] ids = new int[items.size()];
            double[] cumulative = new double[items.size()];
            double total = 0;
            for (int i = 0; i < ids.length; i++) {
                ids[i] = items.get(i);
                total += Math.max(1e-9, weight.applyAsDouble(ids[i]));
                cumulative[i] = total;
            }
            return new Sampler(ids, cumulative);
        }

        int next(Random random) {
            double x = random.nextDouble() * cumulative[cumulative.length - 1];
            int i = Arrays.binarySearch(cumulative, x);
            if (i < 0) i = -i - 1;
            return items[Math.min(i, items.length - 1)];
        }
    }
}

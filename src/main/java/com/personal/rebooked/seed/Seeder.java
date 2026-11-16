package com.personal.rebooked.seed;

import com.personal.rebooked.book.BookService;
import com.personal.rebooked.book.dto.CreateBookDto;
import com.personal.rebooked.book.models.Book;
import com.personal.rebooked.book.models.BookLocation;
import com.personal.rebooked.book.models.Tag;
import org.bson.types.ObjectId;
import org.springframework.data.mongodb.core.query.BasicQuery;
import com.personal.rebooked.book.repositories.TagRepository;
import com.personal.rebooked.category.CategoryService;
import com.personal.rebooked.category.dto.CreateCategoryDTO;
import com.personal.rebooked.category.models.Category;
import com.personal.rebooked.file.FileService;
import com.personal.rebooked.file.models.File;
import com.personal.rebooked.user.UserService;
import com.personal.rebooked.user.dto.CreateUserDto;
import com.personal.rebooked.user.models.Profile;
import com.personal.rebooked.user.models.User;
import com.personal.rebooked.utils.Constants;
import com.personal.rebooked.utils.Misc;
import lombok.RequiredArgsConstructor;
import opennlp.tools.parser.Cons;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.model.Filters;
import com.mongodb.client.model.Projections;
import com.mongodb.client.model.Updates;
import org.bson.Document;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Random;
import java.util.logging.Logger;
import java.util.stream.Collectors;

import static com.personal.rebooked.utils.Misc.generateRandomPrice;
import static com.personal.rebooked.utils.Misc.getRandomElement;

@RequiredArgsConstructor
@Component
public class Seeder implements ApplicationRunner {


    private final UserService userService;

    private final CategoryService categoryService;

    private final Logger logger = Logger.getLogger(Seeder.class.getName());
    private final TagRepository tagRepository;
    private final FileService fileService;
    private final BookService bookService;
    private final MongoTemplate mongoTemplate;

    @Override
    public void run(ApplicationArguments args) throws Exception {
        seedSuperAdmin();
        seedCategories();
        migrateAddresses();
        migrateOnboardingFlag();
        expireLegacyEmailCodes();
        migrateBookLocations();
        migrateBookSlugs();
        dropLegacyNotifications();
//        seedBooks();
    }

    /** Creates the super admin once, already verified. Never sends email. */
    private void seedSuperAdmin() {
        String email = Constants.superAdminDetails.email();
        if (userService.existsByEmail(email)) {
            // Admins seeded before this change went through sign-up and were left unverified
            mongoTemplate.updateFirst(new Query(Criteria.where("email").is(email).and("isEmailVerified").ne(true)),
                    new Update().set("isEmailVerified", true).unset("confirmEmailToken").unset("confirmEmailTokenTTL"),
                    User.class);
            return;
        }
        User user = userService.createSeededUser(Constants.superAdminDetails);
        logger.info(String.format("Super admin %s created", user.getFullName()));
    }

    /**
     * One-off, idempotent: mark existing profiles that already have a display name and city as onboarded,
     * and drop the old User.isOnboarded field. Safe to delete once it has run everywhere.
     */
    private void migrateOnboardingFlag() {
        Query complete = new Query(new Criteria().andOperator(
                Criteria.where("onboardingCompleted").ne(true),
                Criteria.where("userName").exists(true).nin(null, "").not().regex("^\\s*$"),
                Criteria.where("address.state").exists(true).nin(null, "").not().regex("^\\s*$"),
                Criteria.where("address.city").exists(true).nin(null, "").not().regex("^\\s*$")
        ));
        long updated = mongoTemplate.updateMulti(complete, Update.update("onboardingCompleted", true), Profile.class)
                .getModifiedCount();
        if (updated > 0) logger.info(String.format("Marked %d existing profiles as onboarded", updated));

        mongoTemplate.updateMulti(new Query(Criteria.where("isOnboarded").exists(true)),
                new Update().unset("isOnboarded"), User.class);
    }

    /**
     * One-off, idempotent: default address.country to "NG" and normalise address.state to the canonical names.
     * Runs before migrateOnboardingFlag so normalised states count towards onboarding.
     */
    private void migrateAddresses() {
        Query noCountry = new Query(new Criteria().andOperator(
                Criteria.where("address").type(3), // 3 = embedded document
                new Criteria().orOperator(
                        Criteria.where("address.country").exists(false),
                        Criteria.where("address.country").in(null, ""))));
        mongoTemplate.updateMulti(noCountry, Update.update("address.country", Constants.DEFAULT_COUNTRY), Profile.class);

        MongoCollection<Document> profiles = mongoTemplate.getCollection(mongoTemplate.getCollectionName(Profile.class));
        int normalised = 0;
        for (Document doc : profiles.find(Filters.type("address.state", org.bson.BsonType.STRING))
                .projection(Projections.include("address.state"))) {
            String current = doc.get("address", Document.class).getString("state");
            String canonical = Misc.normalizeNigerianState(current);
            if (canonical != null && !canonical.equals(current)) {
                profiles.updateOne(Filters.eq("_id", doc.get("_id")), Updates.set("address.state", canonical));
                normalised++;
            }
        }
        if (normalised > 0) logger.info(String.format("Normalised %d address states", normalised));
    }

    /** Codes issued under the old 2-week expiry are invalidated; users can request a new one. */
    private void expireLegacyEmailCodes() {
        LocalDateTime maxValid = LocalDateTime.now().plusMinutes(Constants.EMAIL_CODE_TTL_MINUTES);
        mongoTemplate.updateMulti(new Query(Criteria.where("confirmEmailTokenTTL").gt(maxValid)),
                new Update().unset("confirmEmailToken").unset("confirmEmailTokenTTL"), User.class);
        mongoTemplate.updateMulti(new Query(Criteria.where("changePasswordTokenTTL").gt(maxValid)),
                new Update().unset("changePasswordToken").unset("changePasswordTokenTTL"), User.class);
    }

    /** Copy each seller's state/city onto their books (17a). Idempotent: only books without a location. */
    private void migrateBookLocations() {
        MongoCollection<Document> books = mongoTemplate.getCollection("book");
        Document missing = new Document("location", new Document("$exists", false));
        List<ObjectId> sellerIds = books.distinct("user.$id", missing, ObjectId.class).into(new ArrayList<>());
        int updated = 0;
        for (ObjectId sellerId : sellerIds) {
            User seller = mongoTemplate.findById(sellerId.toHexString(), User.class);
            BookLocation location = BookService.locationOf(seller);
            if (location == null) continue;
            updated += (int) mongoTemplate.updateMulti(
                    new BasicQuery(new Document("user.$id", sellerId).append("location", new Document("$exists", false))),
                    new Update().set("location", location), Book.class).getModifiedCount();
        }
        if (updated > 0) logger.info("Backfilled location on " + updated + " books");
    }

    /** Give every book a URL slug (16b). Idempotent: only books without one. */
    private void migrateBookSlugs() {
        MongoCollection<Document> books = mongoTemplate.getCollection("book");
        int updated = 0;
        for (Document book : books.find(new Document("slug", new Document("$exists", false)))
                .projection(Projections.include("title"))) {
            String slug = bookService.uniqueSlug(book.getString("title"));
            books.updateOne(Filters.eq("_id", book.get("_id")), Updates.set("slug", slug));
            updated++;
        }
        if (updated > 0) logger.info("Backfilled slug on " + updated + " books");
    }

    /** The pre-#19 "notification" collection (DBRef model) is replaced by "notifications". */
    private void dropLegacyNotifications() {
        if (mongoTemplate.collectionExists("notification")) {
            mongoTemplate.dropCollection("notification");
            logger.info("Dropped the legacy notification collection");
        }
    }

    private void seedCategories() {
        List<CreateCategoryDTO> categoryDTOList = Constants.categoryList;
        for (CreateCategoryDTO createCategoryDTO : categoryDTOList) {
            categoryService.upsert(createCategoryDTO);
        }
    }

    private void seedBooks() {
        // Future implementation for seeding books
        // Fetch all available IDs
        List<String> categoryIds = categoryService.findAll()
                .stream()
                .map(Category::getId)
                .toList();

        List<String> tagNames = tagRepository.findAll()
                .stream()
                .map(Tag::getName)
                .toList();

        List<String> imageIds = fileService.getAllFiles()
                .stream()
                .map(File::getId)
                .toList();

        List<CreateBookDto> books = new ArrayList<>();

        for (Constants.BookData bookData : Constants.SAMPLE_BOOKS) {
            CreateBookDto bookDto = createBookDto(bookData, categoryIds, tagNames, imageIds);
            books.add(bookDto);
        }

        while (books.size() < 500) {
            Constants.BookData generatedBook = generateRandomBook();
            books.add(createBookDto(generatedBook, categoryIds, tagNames, imageIds));
        }

        User adminUser = userService.findUserByEmail(Constants.superAdminDetails.email());
        for (CreateBookDto bookDto : books) {
            try {
                bookService.createForSeed(adminUser, bookDto);
            } catch (Exception e) {
                logger.warning(String.format("Failed to add book %s: %s", bookDto.title(), e.getMessage()));
            }
        }
    }

    private CreateBookDto createBookDto(Constants.BookData bookData, List<String> categoryIds,
                                        List<String> tagNames, List<String> imageIds) {
        List<String> images = Misc.getRandomIds(imageIds, 1, Constants.BOOK_MAX_IMAGES);
        Constants.BookCondition[] conditions = Constants.BookCondition.values();
        return new CreateBookDto(
                bookData.title(),
                bookData.author(),
                Misc.generateRandomPrice(),
                false,
                conditions[new Random().nextInt(conditions.length)],
                null,
                bookData.description(),
                images.isEmpty() ? null : images.get(0), // cover is one of the photos
                images,
                Misc.getRandomIds(categoryIds, 1, 10), // 1-10 categories
                tagNames.isEmpty() ? null : Misc.getRandomIds(tagNames, 0, 3), // 0-3 tags
                "ACTIVE"
        );
    }

    private Constants.BookData generateRandomBook() {
        Random random = new Random();
        String title;
        String author = getRandomElement(Constants.AUTHORS);
        String description;

        // Generate title using various patterns
        if (random.nextBoolean()) {
            // Pattern: Prefix + Suffix
            title = getRandomElement(Constants.TITLE_PREFIXES) + " " + getRandomElement(Constants.TITLE_SUFFIXES);
        } else {
            // Pattern: The + Adjective + Noun
            List<String> adjectives = Arrays.asList("Ancient", "Broken", "Silent", "Golden",
                    "Forgotten", "Sacred", "Dark", "Bright", "Eternal", "Mysterious");
            List<String> nouns = Arrays.asList("Journey", "Secret", "Promise", "Dream",
                    "Legend", "Story", "Mystery", "Adventure", "Quest", "Discovery");
            title = "The " + getRandomElement(adjectives) + " " + getRandomElement(nouns);
        }

        description = generateDescription(title);

        return new Constants.BookData(title, author, description);
    }

    private String generateDescription(String title) {
        List<String> templates = Arrays.asList(
                "A captivating tale that explores the depths of %s and the human condition.",
                "An extraordinary journey through %s that will leave readers questioning everything.",
                "A masterful exploration of %s, weaving together themes of love, loss, and redemption.",
                "Set against the backdrop of %s, this novel delves into the complexities of modern life.",
                "A thought-provoking examination of %s that challenges conventional wisdom.",
                "An unforgettable story of courage and determination in the face of %s.",
                "A richly detailed narrative that brings %s to vivid life on every page.",
                "A compelling drama that unfolds against the setting of %s and human resilience."
        );

        List<String> themes = Arrays.asList(
                "family dynamics", "societal change", "personal growth", "historical events",
                "technological advancement", "cultural identity", "moral dilemmas", "human nature",
                "social justice", "environmental challenges", "political upheaval", "spiritual awakening"
        );

        return String.format(getRandomElement(templates), getRandomElement(themes));
    }
}

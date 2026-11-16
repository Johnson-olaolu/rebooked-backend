package com.personal.rebooked.book;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.gson.Gson;
import com.personal.rebooked.book.dto.*;
import com.personal.rebooked.book.models.Book;
import com.personal.rebooked.book.models.BookLocation;
import com.personal.rebooked.book.models.Tag;
import com.personal.rebooked.book.repositories.BookRepository;
import com.personal.rebooked.book.repositories.TagRepository;
import com.personal.rebooked.category.models.Category;
import com.personal.rebooked.category.repositories.CategoryRepository;
import com.personal.rebooked.exception.ApiException;
import com.personal.rebooked.file.FileService;
import com.personal.rebooked.file.models.File;
import com.personal.rebooked.file.respositories.FileRepository;
import com.personal.rebooked.notification.BookSnapshot;
import com.personal.rebooked.notification.NotificationService;
import com.personal.rebooked.notification.Notifications;
import com.personal.rebooked.service.AiService;
import com.personal.rebooked.service.RedisService;
import com.personal.rebooked.user.models.User;
import com.personal.rebooked.utils.Constants;
import com.personal.rebooked.utils.Misc;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.bson.Document;
import org.bson.types.ObjectId;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.aggregation.*;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.regex.Pattern;

import static org.springframework.data.mongodb.core.aggregation.Aggregation.lookup;

@Slf4j
@RequiredArgsConstructor
@Service
public class BookService {

    private static final String AI_PICKS_KEY = "ai_recommended_book_ids";
    private static final int AI_PICKS_SAMPLE = 50;

    private final BookRepository bookRepository;
    private final FileRepository fileRepository;
    private final FileService fileService;
    private final CategoryRepository categoryRepository;
    private final TagRepository tagRepository;
    private final MongoTemplate mongoTemplate;
    private final AiService aiService;
    private final RedisService redisService;
    private final ApplicationEventPublisher events;
    private final NotificationService notificationService;
    private final Notifications notifications;
    private final com.personal.rebooked.conversation.ConversationService conversationService;

    /** View counts that earn the seller a VIEWS_MILESTONE notification. */

    // ---------------------------------------------------------------- create / update

    public Book create(User user, CreateBookDto dto) {
        return create(user, dto, false);
    }

    /** Seeder only: builds books from files the admin did not upload, so skips the photo ownership check. */
    public Book createForSeed(User user, CreateBookDto dto) {
        return create(user, dto, true);
    }

    private Book create(User user, CreateBookDto dto, boolean skipOwnership) {
        Map<String, String> errors = new LinkedHashMap<>();

        Constants.BookStatus status = Constants.BookStatus.ACTIVE;
        if (dto.status() != null && !dto.status().isBlank()) {
            String requested = dto.status().trim().toUpperCase(Locale.ROOT);
            if (requested.equals("ACTIVE") || requested.equals("DRAFT")) {
                status = Constants.BookStatus.valueOf(requested);
            } else {
                errors.put("status", "Status must be ACTIVE or DRAFT");
            }
        }

        Book book = new Book();
        book.setUser(user);
        book.setStatus(status);
        book.setTitle(dto.title() == null ? null : dto.title().trim());

        BookChanges changes = new BookChanges(dto.author(), dto.price(), dto.isFree(), dto.condition(), dto.isbn(),
                dto.description(), dto.coverImageId(), dto.imageIds(), dto.categoryIds(),
                dto.tagNames() == null ? List.of() : dto.tagNames());
        applyChanges(book, changes, skipOwnership ? null : user.getId(), errors);

        if (status == Constants.BookStatus.ACTIVE) {
            validateForPublish(book, errors);
        }
        throwIfErrors(errors);
        book.setTags(resolveTags(changes.tagNames()));
        book.setLocation(locationOf(user));
        book.setSlug(uniqueSlug(book.getTitle()));
        return bookRepository.save(book);
    }

    public Book update(User caller, String id, UpdateBookDTO dto) {
        Book book = findById(id);
        requireOwner(book, caller);
        Map<String, String> errors = new LinkedHashMap<>();
        List<File> previousFiles = filesOf(book);
        String previousTitle = book.getTitle();

        if (dto.title() != null) book.setTitle(dto.title().trim());
        BookChanges changes = new BookChanges(dto.author(), dto.price(), dto.isFree(), dto.condition(), dto.isbn(),
                dto.description(), dto.coverImageId(), dto.imageIds(), dto.categoryIds(), dto.tagNames());
        applyChanges(book, changes, caller.getId(), errors);

        // A live listing must stay complete
        if (book.getStatus() == Constants.BookStatus.ACTIVE) {
            validateForPublish(book, errors);
        }
        throwIfErrors(errors);
        if (changes.tagNames() != null) book.setTags(resolveTags(changes.tagNames()));
        book.setLocation(locationOf(book.getUser()));
        // Published links stay stable; drafts follow their title
        if (book.getSlug() == null || (book.getStatus() == Constants.BookStatus.DRAFT
                && !Objects.equals(previousTitle, book.getTitle()))) {
            book.setSlug(uniqueSlug(book.getTitle()));
        }
        Book saved = bookRepository.save(book);
        events.publishEvent(new BookChangedEvent(saved, BookChangedEvent.Kind.UPDATED, saved.getStatus()));

        Set<String> kept = new HashSet<>();
        filesOf(saved).forEach(f -> kept.add(f.getId()));
        for (File file : previousFiles) {
            if (!kept.contains(file.getId())) fileService.deleteIfUnused(file, caller.getId());
        }
        return saved;
    }

    public Book updateStatus(User caller, String id, UpdateBookStatusDTO dto) {
        Book book = findById(id);
        requireOwner(book, caller);
        Constants.BookStatus target = dto.status();
        if (target == null) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Validation failed", Map.of("status", "Status is required"));
        }
        if (target == Constants.BookStatus.DRAFT && book.getStatus() != Constants.BookStatus.DRAFT) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Validation failed",
                    Map.of("status", "A published book can't go back to draft; use INACTIVE"));
        }
        if (target == Constants.BookStatus.ACTIVE) {
            Map<String, String> errors = new LinkedHashMap<>();
            validateForPublish(book, errors);
            throwIfErrors(errors);
        }
        if (target == Constants.BookStatus.SOLD) return markSold(caller, book, dto.buyerId());

        Constants.BookStatus previous = book.getStatus();
        book.setSoldDate(null);
        // Leaving SOLD (e.g. back to ACTIVE): no buyer any more
        if (target != previous) book.setBuyerId(null);
        book.setStatus(target);
        Book saved = bookRepository.save(book);
        if (target != previous) {
            events.publishEvent(new BookChangedEvent(saved, BookChangedEvent.Kind.STATUS_CHANGED, previous));
        }
        return saved;
    }

    /**
     * SOLD always names the buyer: someone who chatted with the seller about this book.
     * Does exactly what the chat's "Mark as sold to X" does.
     */
    private Book markSold(User caller, Book book, String buyerId) {
        if (buyerId == null || buyerId.isBlank()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Choose who bought it", Map.of("buyerId", "Choose who bought it"));
        }
        if (book.getStatus() == Constants.BookStatus.SOLD && buyerId.equals(book.getBuyerId())) return book; // a retry
        var conversation = conversationService.sellableConversation(book.getId(), caller.getId(), buyerId)
                .orElseThrow(() -> new ApiException(HttpStatus.BAD_REQUEST, "Pick someone who chatted with you about this book",
                        Map.of("buyerId", "Pick someone who chatted with you about this book")));
        if (conversationService.markSoldTo(book, conversation, caller.getId()) == null) {
            String message = book.getStatus() == Constants.BookStatus.SOLD ? "This book is already sold"
                    : "Only a published book can be marked as sold";
            throw new ApiException(HttpStatus.BAD_REQUEST, message, Map.of("status", message));
        }
        return findById(book.getId());
    }

    /** GET /book/{id}/chat-buyers: only the seller. */
    public List<com.personal.rebooked.conversation.dto.ChatBuyer> chatBuyers(User caller, String id) {
        Book book = findById(id);
        requireOwner(book, caller);
        return conversationService.chatBuyers(id);
    }

    public void delete(User caller, String id) {
        Book book = findById(id);
        requireOwner(book, caller);
        bookRepository.delete(book);
        events.publishEvent(new BookChangedEvent(book, BookChangedEvent.Kind.DELETED, book.getStatus()));
        // Photos no other book or profile uses go too
        for (File file : filesOf(book)) fileService.deleteIfUnused(file, caller.getId());
    }

    /** The cover and images, without duplicates. */
    private static List<File> filesOf(Book book) {
        Map<String, File> files = new LinkedHashMap<>();
        if (book.getImages() != null) book.getImages().forEach(f -> { if (f != null) files.put(f.getId(), f); });
        if (book.getCoverImage() != null) files.putIfAbsent(book.getCoverImage().getId(), book.getCoverImage());
        return new ArrayList<>(files.values());
    }

    public static BookLocation locationOf(User user) {
        if (user == null || user.getProfile() == null || user.getProfile().getAddress() == null) return null;
        var address = user.getProfile().getAddress();
        if (address.getState() == null && address.getCity() == null) return null;
        return new BookLocation(address.getState(), address.getCity());
    }

    /** slugify(title) + 4 random digits, retried on collision; 6 digits as a last resort. */
    public String uniqueSlug(String title) {
        String base = Misc.slugify(title);
        Random random = new Random();
        for (int i = 0; i < 5; i++) {
            String slug = base + "-" + (1000 + random.nextInt(9000));
            if (!bookRepository.existsBySlug(slug)) return slug;
        }
        return base + "-" + (100000 + random.nextInt(900000));
    }

    public Book findVisibleBySlug(String slug, User viewer) {
        Book book = bookRepository.findBySlug(slug)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Book not found"));
        return findVisible(book.getId(), viewer);
    }

    /** Fields shared by create and PATCH. null = not sent. */
    private record BookChanges(String author, Double price, Boolean isFree, Constants.BookCondition condition,
                               String isbn, String description, String coverImageId, List<String> imageIds,
                               List<String> categoryIds, List<String> tagNames) {
    }

    /**
     * Merges the sent fields into the book, collecting field errors instead of throwing.
     * ownerId null skips the "uploaded by the caller" check (seeder only).
     */
    private void applyChanges(Book book, BookChanges c, String ownerId, Map<String, String> errors) {
        if (c.author() != null) book.setAuthor(blankToNull(c.author()));
        if (c.description() != null) book.setDescription(blankToNull(c.description()));
        if (c.condition() != null) book.setCondition(c.condition());

        if (c.isbn() != null) {
            if (c.isbn().isBlank()) {
                book.setIsbn(null);
            } else {
                String isbn = Misc.normalizeIsbn(c.isbn());
                if (isbn == null) errors.put("isbn", "ISBN must be 10 or 13 digits");
                else book.setIsbn(isbn);
            }
        }

        if (c.isFree() != null) book.setFree(c.isFree());
        if (c.price() != null) {
            if (c.price() < 0) errors.put("price", "Price can't be negative");
            else book.setPrice(c.price());
        }
        if (book.isFree()) book.setPrice(0.0);

        if (c.categoryIds() != null) {
            List<Category> categories = new ArrayList<>();
            for (String categoryId : new LinkedHashSet<>(c.categoryIds())) {
                Optional<Category> category = isObjectId(categoryId)
                        ? categoryRepository.findById(categoryId) : Optional.empty();
                if (category.isEmpty()) {
                    errors.put("categoryIds", "Unknown category: " + categoryId);
                    break;
                }
                categories.add(category.get());
            }
            book.setCategories(categories);
        }

        if (c.imageIds() != null) {
            List<String> ids = new ArrayList<>(new LinkedHashSet<>(c.imageIds()));
            if (ids.size() > Constants.BOOK_MAX_IMAGES) {
                errors.put("imageIds", "Add at most " + Constants.BOOK_MAX_IMAGES + " photos");
            } else {
                List<File> images = new ArrayList<>();
                for (String imageId : ids) {
                    Optional<File> file = isObjectId(imageId) ? fileRepository.findById(imageId) : Optional.empty();
                    if (file.isEmpty()) {
                        errors.put("imageIds", "Unknown photo: " + imageId);
                        break;
                    }
                    if (ownerId != null && !FileService.isOwnedBy(file.get(), ownerId)) {
                        errors.put("imageIds", "You can only use photos you uploaded");
                        break;
                    }
                    images.add(file.get());
                }
                book.setImages(images);
            }
            // Whenever the photo list is sent, the cover must be picked from it
            if (!ids.isEmpty() && (c.coverImageId() == null || c.coverImageId().isBlank())) {
                errors.put("coverImageId", "Choose a cover photo");
            }
            if (ids.isEmpty() && (c.coverImageId() == null || c.coverImageId().isBlank())) {
                book.setCoverImage(null);
            }
        }

        if (c.coverImageId() != null && !c.coverImageId().isBlank() && !errors.containsKey("imageIds")) {
            File cover = book.getImages() == null ? null : book.getImages().stream()
                    .filter(f -> f.getId().equals(c.coverImageId()))
                    .findFirst().orElse(null);
            if (cover == null) errors.put("coverImageId", "The cover must be one of the book's photos");
            else book.setCoverImage(cover);
        }
    }

    /** The rules a book must meet to be ACTIVE. */
    private static void validateForPublish(Book book, Map<String, String> errors) {
        if (book.getTitle() == null || book.getTitle().isBlank()) errors.putIfAbsent("title", "Title is required");
        if (book.getAuthor() == null || book.getAuthor().isBlank()) errors.putIfAbsent("author", "Author is required");
        if (book.getCondition() == null) errors.putIfAbsent("condition", "Condition is required");
        if (!book.isFree() && (book.getPrice() == null || book.getPrice() <= 0)) {
            errors.putIfAbsent("price", "Set a price above 0, or list the book for free");
        }
        int images = book.getImages() == null ? 0 : book.getImages().size();
        if (images < 1 || images > Constants.BOOK_MAX_IMAGES) {
            errors.putIfAbsent("imageIds", "Add 1 to " + Constants.BOOK_MAX_IMAGES + " photos");
        } else if (!errors.containsKey("imageIds") && !errors.containsKey("coverImageId")
                && (book.getCoverImage() == null || book.getImages().stream()
                .noneMatch(f -> f.getId().equals(book.getCoverImage().getId())))) {
            errors.putIfAbsent("coverImageId", "Choose a cover photo");
        }
        if (book.getCategories() == null || book.getCategories().isEmpty()) {
            errors.putIfAbsent("categoryIds", "Choose at least one category");
        }
    }

    private List<Tag> resolveTags(List<String> names) {
        List<Tag> tags = new ArrayList<>();
        Set<String> seen = new HashSet<>();
        for (String raw : names) {
            if (raw == null || raw.isBlank()) continue;
            String name = raw.trim();
            if (!seen.add(name.toLowerCase(Locale.ROOT))) continue;
            tags.add(tagRepository.findByName(name)
                    .orElseGet(() -> tagRepository.save(Tag.builder().name(name).build())));
        }
        return tags;
    }

    private static void requireOwner(Book book, User caller) {
        if (caller == null || book.getUser() == null || !caller.getId().equals(book.getUser().getId())) {
            throw new ApiException(HttpStatus.FORBIDDEN, "You can only change your own books");
        }
    }

    private static void throwIfErrors(Map<String, String> errors) {
        if (!errors.isEmpty()) throw new ApiException(HttpStatus.BAD_REQUEST, "Validation failed", errors);
    }

    // ---------------------------------------------------------------- reads

    public Book findById(String id) {
        if (!isObjectId(id)) throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Book not found");
        return bookRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Book not found"));
    }

    /** Public read: drafts are visible only to their owner (404 for everyone else). */
    public Book findVisible(String id, User viewer) {
        Book book = findById(id);
        if (book.getStatus() == Constants.BookStatus.DRAFT
                && (viewer == null || book.getUser() == null || !viewer.getId().equals(book.getUser().getId()))) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Book not found");
        }
        return book;
    }

    public List<Book> findAll(Constants.BookStatus status) {
        if (status != null) {
            return bookRepository.findByStatus(status);
        }
        return bookRepository.findAll();
    }

    /** GET /book/me */
    public ShelfResponse shelf(User owner, Constants.BookStatus status, Integer page, Integer pageSize) {
        int p = page == null || page < 0 ? 0 : page;
        int size = pageSize == null || pageSize <= 0 ? 20 : Math.min(pageSize, 50);
        Criteria ownerCriteria = Criteria.where("user.$id").is(new ObjectId(owner.getId()));

        Criteria listCriteria = status == null ? ownerCriteria
                : new Criteria().andOperator(ownerCriteria, Criteria.where("status").is(status));
        long total = mongoTemplate.count(new Query(listCriteria), Book.class);
        Query listQuery = new Query(listCriteria)
                .with(Sort.by(Sort.Direction.DESC, "updatedAt"))
                .skip((long) p * size)
                .limit(size);
        List<Book> content = mongoTemplate.find(listQuery, Book.class);

        Map<String, Long> counts = new LinkedHashMap<>();
        for (Constants.BookStatus s : Constants.BookStatus.values()) counts.put(s.name(), 0L);
        Aggregation countAgg = Aggregation.newAggregation(
                Aggregation.match(ownerCriteria),
                Aggregation.group("status").count().as("n"));
        for (Document row : mongoTemplate.aggregate(countAgg, "book", Document.class).getMappedResults()) {
            Object key = row.get("_id");
            if (key != null) counts.put(key.toString(), ((Number) row.get("n")).longValue());
        }

        return new ShelfResponse(content, p, size, total, (int) Math.ceil((double) total / size), counts);
    }

    /** Filter groups for the shop query. Facets count each group with every other group applied, but not its own. */
    private record Filters(List<Criteria> base, Map<String, Criteria> groups, boolean searching) {
        Criteria all() {
            return except(null);
        }

        Criteria except(String group) {
            List<Criteria> criteria = new ArrayList<>(base);
            groups.forEach((name, c) -> {
                if (!name.equals(group)) criteria.add(c);
            });
            return new Criteria().andOperator(criteria.toArray(new Criteria[0]));
        }
    }

    private static final ZoneId SHOP_ZONE = ZoneId.of("Africa/Lagos");

    /** Shop search (ACTIVE books) or a seller's sold books (status=SOLD with userId). */
    public BookQueryResponse query(QueryBookDTO dto) {
        Map<String, String> errors = new LinkedHashMap<>();

        boolean hasUser = dto.userId() != null && !dto.userId().isBlank();
        if (hasUser && !isObjectId(dto.userId())) errors.put("userId", "Invalid user id");
        if (dto.categoryIds().stream().anyMatch(id -> !isObjectId(id))) errors.put("categoryIds", "Invalid category id");

        Constants.BookStatus status = Constants.BookStatus.ACTIVE;
        if (dto.status() != null && !dto.status().isBlank()) {
            String s = dto.status().trim().toUpperCase(Locale.ROOT);
            if (s.equals("ACTIVE") || s.equals("SOLD")) status = Constants.BookStatus.valueOf(s);
            else errors.put("status", "Status must be ACTIVE or SOLD");
        }
        if (status == Constants.BookStatus.SOLD && !hasUser && !errors.containsKey("userId")) {
            errors.put("userId", "userId is required to list sold books");
        }

        List<String> states = new ArrayList<>();
        for (String raw : dto.states()) {
            if (raw == null || raw.isBlank()) continue;
            String state = Misc.normalizeNigerianState(raw);
            if (state == null) {
                errors.put("states", "Unknown state: " + raw.trim());
                break;
            }
            states.add(state);
        }

        List<Constants.BookCondition> conditions = new ArrayList<>();
        for (String raw : dto.conditions()) {
            if (raw == null || raw.isBlank()) continue;
            try {
                conditions.add(Constants.BookCondition.valueOf(raw.trim().toUpperCase(Locale.ROOT)));
            } catch (IllegalArgumentException e) {
                errors.put("conditions", "Condition must be one of NEW, LIKE_NEW, GOOD, FAIR");
                break;
            }
        }

        if (dto.minPrice() != null && dto.minPrice() < 0) errors.put("minPrice", "Price can't be negative");
        if (dto.maxPrice() != null && dto.maxPrice() < 0) errors.put("maxPrice", "Price can't be negative");
        if (dto.minPrice() != null && dto.maxPrice() != null && dto.minPrice() > dto.maxPrice()
                && !errors.containsKey("minPrice") && !errors.containsKey("maxPrice")) {
            errors.put("minPrice", "Minimum price can't be more than the maximum");
        }

        String sort = dto.sort() == null || dto.sort().isBlank() ? "NEWEST" : dto.sort().trim().toUpperCase(Locale.ROOT);
        if (!List.of("NEWEST", "PRICE_ASC", "PRICE_DESC").contains(sort)) {
            errors.put("sort", "Sort must be NEWEST, PRICE_ASC or PRICE_DESC");
        }
        throwIfErrors(errors);

        // ---- base filters: status, seller, search
        List<Criteria> base = new ArrayList<>();
        base.add(Criteria.where("status").is(status));
        if (hasUser) base.add(Criteria.where("user.$id").is(new ObjectId(dto.userId())));

        boolean searching = dto.search() != null && !dto.search().trim().isEmpty();
        if (searching) {
            String text = dto.search().trim();
            Pattern pattern = Pattern.compile(Pattern.quote(text), Pattern.CASE_INSENSITIVE);
            List<Criteria> search = new ArrayList<>(List.of(
                    Criteria.where("title").regex(pattern),
                    Criteria.where("author").regex(pattern),
                    Criteria.where("categoryDetails.name").regex(pattern)));
            String isbnText = text.replaceAll("[\\s-]", "").toUpperCase(Locale.ROOT);
            if (!isbnText.isEmpty() && isbnText.matches("[0-9X]+")) {
                search.add(Criteria.where("isbn").regex(Pattern.compile(Pattern.quote(isbnText))));
            }
            base.add(new Criteria().orOperator(search.toArray(new Criteria[0])));
        }

        // ---- optional groups
        Map<String, Criteria> groups = new LinkedHashMap<>();
        if (!dto.categoryIds().isEmpty()) {
            groups.put("categories", Criteria.where("categories.$id")
                    .in(dto.categoryIds().stream().map(ObjectId::new).toList()));
        }
        if (!states.isEmpty()) groups.put("states", Criteria.where("location.state").in(states));
        if (!conditions.isEmpty()) groups.put("conditions", Criteria.where("condition").in(conditions));
        if (dto.minPrice() != null || dto.maxPrice() != null) {
            Criteria price = Criteria.where("price");
            if (dto.minPrice() != null) price = price.gte(dto.minPrice());
            if (dto.maxPrice() != null) price = price.lte(dto.maxPrice());
            groups.put("price", price);
        }
        if (Boolean.TRUE.equals(dto.free())) groups.put("free", Criteria.where("isFree").is(true));
        Filters filters = new Filters(base, groups, searching);

        // ---- page
        List<AggregationOperation> operations = new ArrayList<>();
        if (searching) operations.add(lookup("category", "categories.$id", "_id", "categoryDetails"));
        operations.add(Aggregation.match(filters.all()));

        List<AggregationOperation> countOps = new ArrayList<>(operations);
        countOps.add(Aggregation.count().as("n"));
        Document countRow = mongoTemplate.aggregate(Aggregation.newAggregation(countOps), "book", Document.class)
                .getUniqueMappedResult();
        long total = countRow == null ? 0 : ((Number) countRow.get("n")).longValue();

        Sort order;
        if (status == Constants.BookStatus.SOLD) {
            order = Sort.by(Sort.Order.desc("soldDate"), Sort.Order.desc("createdAt"));
        } else if (sort.equals("PRICE_ASC")) {
            order = Sort.by(Sort.Order.asc("price"), Sort.Order.desc("createdAt"));
        } else if (sort.equals("PRICE_DESC")) {
            order = Sort.by(Sort.Order.desc("price"), Sort.Order.desc("createdAt"));
        } else {
            order = Sort.by(Sort.Order.desc("createdAt"));
        }
        operations.add(Aggregation.sort(order));
        operations.add(Aggregation.skip((long) dto.page() * dto.pageSize()));
        operations.add(Aggregation.limit(dto.pageSize()));
        if (searching) operations.add(Aggregation.project().andExclude("categoryDetails"));
        List<Book> books = mongoTemplate.aggregate(Aggregation.newAggregation(operations), "book", Book.class)
                .getMappedResults();

        int totalPages = (int) Math.ceil((double) total / dto.pageSize());
        if (status != Constants.BookStatus.ACTIVE) {
            return new BookQueryResponse(books, dto.page(), dto.pageSize(), total, totalPages, null, null);
        }
        FacetResult facets = facets(filters);
        return new BookQueryResponse(books, dto.page(), dto.pageSize(), total, totalPages, facets.facets(), facets.addedToday());
    }

    private record FacetResult(BookQueryResponse.Facets facets, long addedToday) {
    }

    /** One $facet aggregation: each branch applies every filter group except its own. */
    private FacetResult facets(Filters filters) {
        Date startOfToday = Date.from(LocalDate.now(SHOP_ZONE).atStartOfDay(SHOP_ZONE).toInstant());
        AggregationOperation countAll = ctx -> new Document("$count", "n");

        List<AggregationOperation> operations = new ArrayList<>();
        if (filters.searching()) operations.add(lookup("category", "categories.$id", "_id", "categoryDetails"));
        operations.add(Aggregation.match(new Criteria().andOperator(filters.base().toArray(new Criteria[0]))));
        operations.add(Aggregation
                .facet(Aggregation.match(filters.except("categories")),
                        Aggregation.unwind("categories"),
                        // DBRef ids can't be referenced as "$categories.$id" in expressions; read them via $objectToArray
                        ctx -> new Document("$project", new Document("cid", new Document("$arrayElemAt", List.of(
                                new Document("$map", new Document("input", new Document("$objectToArray", "$categories"))
                                        .append("in", "$$this.v")), 1)))),
                        ctx -> new Document("$group", new Document("_id", "$cid").append("n", new Document("$sum", 1))))
                .as("categories")
                .and(Aggregation.match(filters.except("states")),
                        ctx -> new Document("$match", new Document("location.state", new Document("$ne", null))),
                        ctx -> new Document("$group", new Document("_id", "$location.state").append("n", new Document("$sum", 1))))
                .as("states")
                .and(Aggregation.match(filters.except("conditions")),
                        ctx -> new Document("$match", new Document("condition", new Document("$ne", null))),
                        ctx -> new Document("$group", new Document("_id", "$condition").append("n", new Document("$sum", 1))))
                .as("conditions")
                .and(Aggregation.match(filters.except("free")),
                        ctx -> new Document("$match", new Document("isFree", true)),
                        countAll)
                .as("free")
                .and(Aggregation.match(filters.all()),
                        ctx -> new Document("$match", new Document("createdAt", new Document("$gte", startOfToday))),
                        countAll)
                .as("addedToday"));

        Document row = mongoTemplate.aggregate(Aggregation.newAggregation(operations), "book", Document.class)
                .getUniqueMappedResult();
        if (row == null) row = new Document();

        Map<String, Long> conditionCounts = new LinkedHashMap<>();
        for (Constants.BookCondition c : Constants.BookCondition.values()) conditionCounts.put(c.name(), 0L);
        conditionCounts.putAll(groupCounts(row, "conditions"));

        BookQueryResponse.Facets facets = new BookQueryResponse.Facets(
                groupCounts(row, "categories"), groupCounts(row, "states"), conditionCounts, singleCount(row, "free"));
        return new FacetResult(facets, singleCount(row, "addedToday"));
    }

    private static Map<String, Long> groupCounts(Document row, String key) {
        Map<String, Long> counts = new LinkedHashMap<>();
        List<Document> rows = row.getList(key, Document.class, List.of());
        rows.stream()
                .filter(d -> d.get("_id") != null)
                .sorted((a, b) -> Long.compare(((Number) b.get("n")).longValue(), ((Number) a.get("n")).longValue()))
                .forEach(d -> counts.put(d.get("_id").toString(), ((Number) d.get("n")).longValue()));
        return counts;
    }

    private static long singleCount(Document row, String key) {
        List<Document> rows = row.getList(key, Document.class, List.of());
        return rows.isEmpty() ? 0 : ((Number) rows.get(0).get("n")).longValue();
    }

    public List<Book> getSoldBooks(QuerySoldBooksDTO dto) {
        Date startDate = Misc.calculateStartDate(dto.timeQuery());
        List<Criteria> criteria = new ArrayList<>();
        criteria.add(Criteria.where("status").is(Constants.BookStatus.SOLD));
        criteria.add(Criteria.where("soldDate").gte(startDate).lte(new Date()));
        if (dto.userId() != null && !dto.userId().isBlank()) {
            if (!isObjectId(dto.userId())) {
                throw new ApiException(HttpStatus.BAD_REQUEST, "Validation failed", Map.of("userId", "Invalid user id"));
            }
            criteria.add(Criteria.where("user.$id").is(new ObjectId(dto.userId())));
        }
        return mongoTemplate.find(new Query(new Criteria().andOperator(criteria.toArray(new Criteria[0]))), Book.class);
    }

    /** Counts at most one view per viewer per book per day, and only for ACTIVE books. */
    /** One query for the books, in the order of {@code ids}; unknown ids are dropped. */
    public List<Book> booksInOrder(List<String> ids) {
        List<String> valid = ids.stream().filter(BookService::isObjectId).toList();
        if (valid.isEmpty()) return List.of();
        Map<String, Book> byId = new HashMap<>();
        for (Book book : bookRepository.findAllById(valid)) byId.put(book.getId(), book);
        List<Book> ordered = new ArrayList<>();
        for (String id : valid) {
            Book book = byId.get(id);
            if (book != null) ordered.add(book);
        }
        return ordered;
    }

    /** GET /book/recently-viewed: newest first; ACTIVE and SOLD books only, never the viewer's own. */
    public List<Book> recentlyViewed(User viewer, List<String> ids) {
        return booksInOrder(ids).stream()
                .filter(b -> b.getStatus() == Constants.BookStatus.ACTIVE || b.getStatus() == Constants.BookStatus.SOLD)
                .filter(b -> b.getUser() == null || !viewer.getId().equals(b.getUser().getId()))
                .toList();
    }

    @SuppressWarnings("unchecked")
    public List<Book> getAIRecommendedBooks() {
        Object cached = redisService.get(AI_PICKS_KEY);
        if (cached instanceof List<?> ids && !ids.isEmpty()) {
            // Re-read so sold, deleted or unpublished books drop out
            List<Book> books = activeBooksInOrder((List<String>) ids);
            if (!books.isEmpty()) return books;
        }
        try {
            List<Book> books = new ArrayList<>(findAll(Constants.BookStatus.ACTIVE));
            if (books.isEmpty()) return List.of();
            Collections.shuffle(books, new Random());
            books = books.subList(0, Math.min(AI_PICKS_SAMPLE, books.size()));

            List<Map<String, String>> bookList = new ArrayList<>();
            for (Book book : books) {
                Map<String, String> bookMap = new HashMap<>();
                bookMap.put("id", book.getId());
                bookMap.put("title", book.getTitle());
                bookMap.put("author", book.getAuthor());
                bookMap.put("description", book.getDescription());
                bookMap.put("viewCount", String.valueOf(book.getViewCount()));
                bookMap.put("categories", book.getCategories() == null ? "" : book.getCategories().stream()
                        .map(Category::getName).reduce((a, b) -> a + ", " + b).orElse(""));
                bookList.add(bookMap);
            }
            String booksJson = new ObjectMapper().writeValueAsString(bookList);
            String response = aiService.suggestBooks(booksJson);
            List<String> ids = new Gson().fromJson(response, List.class);
            List<Book> picks = activeBooksInOrder(ids);
            redisService.save(AI_PICKS_KEY, picks.stream().map(Book::getId).toList(), 7, TimeUnit.DAYS);
            return picks;
        } catch (Exception e) {
            log.error("Failed to fetch AI recommended books", e);
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Failed to fetch AI recommended books");
        }
    }

    private List<Book> activeBooksInOrder(List<String> ids) {
        List<Book> books = new ArrayList<>();
        for (String id : ids) {
            if (!isObjectId(id)) continue;
            bookRepository.findById(id)
                    .filter(b -> b.getStatus() == Constants.BookStatus.ACTIVE)
                    .ifPresent(books::add);
        }
        return books;
    }

    public List<Book> getTopBooks() {
        return bookRepository.findTop10ByStatusOrderByViewCountDesc(Constants.BookStatus.ACTIVE);
    }

    // ---------------------------------------------------------------- helpers

    private static boolean isObjectId(String id) {
        return id != null && ObjectId.isValid(id);
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}

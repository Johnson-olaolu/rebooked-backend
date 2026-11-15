package com.personal.rebooked.book;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.gson.Gson;
import com.personal.rebooked.book.dto.*;
import com.personal.rebooked.book.models.Book;
import com.personal.rebooked.book.models.Tag;
import com.personal.rebooked.book.repositories.BookRepository;
import com.personal.rebooked.book.repositories.TagRepository;
import com.personal.rebooked.category.models.Category;
import com.personal.rebooked.category.repositories.CategoryRepository;
import com.personal.rebooked.exception.ApiException;
import com.personal.rebooked.file.FileService;
import com.personal.rebooked.file.models.File;
import com.personal.rebooked.file.respositories.FileRepository;
import com.personal.rebooked.service.AiService;
import com.personal.rebooked.service.RedisService;
import com.personal.rebooked.user.models.User;
import com.personal.rebooked.utils.Constants;
import com.personal.rebooked.utils.Misc;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.bson.Document;
import org.bson.types.ObjectId;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.aggregation.*;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

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
    private final CategoryRepository categoryRepository;
    private final TagRepository tagRepository;
    private final MongoTemplate mongoTemplate;
    private final AiService aiService;
    private final RedisService redisService;

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
        return bookRepository.save(book);
    }

    public Book update(User caller, String id, UpdateBookDTO dto) {
        Book book = findById(id);
        requireOwner(book, caller);
        Map<String, String> errors = new LinkedHashMap<>();

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
        return bookRepository.save(book);
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
        book.setSoldDate(target == Constants.BookStatus.SOLD ? new Date() : null);
        book.setStatus(target);
        return bookRepository.save(book);
    }

    public void delete(User caller, String id) {
        Book book = findById(id);
        requireOwner(book, caller);
        bookRepository.delete(book);
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

    /** Shop search: ACTIVE books only, newest first. */
    public PageResponse<Book> query(QueryBookDTO queryDTO) {
        Map<String, String> errors = new LinkedHashMap<>();
        if (queryDTO.userId() != null && !queryDTO.userId().isBlank() && !isObjectId(queryDTO.userId())) {
            errors.put("userId", "Invalid user id");
        }
        if (queryDTO.categoryIds().stream().anyMatch(id -> !isObjectId(id))) {
            errors.put("categoryIds", "Invalid category id");
        }
        throwIfErrors(errors);

        List<AggregationOperation> operations = new ArrayList<>();
        List<Criteria> criteria = new ArrayList<>();
        criteria.add(Criteria.where("status").is(Constants.BookStatus.ACTIVE));

        if (queryDTO.userId() != null && !queryDTO.userId().isBlank()) {
            criteria.add(Criteria.where("user.$id").is(new ObjectId(queryDTO.userId())));
        }
        if (!queryDTO.categoryIds().isEmpty()) {
            criteria.add(Criteria.where("categories.$id").in(queryDTO.categoryIds().stream().map(ObjectId::new).toList()));
        }

        boolean searching = queryDTO.search() != null && !queryDTO.search().trim().isEmpty();
        if (searching) {
            // Only needed to search category names
            operations.add(lookup("category", "categories.$id", "_id", "categoryDetails"));
            Pattern pattern = Pattern.compile(Pattern.quote(queryDTO.search().trim()), Pattern.CASE_INSENSITIVE);
            criteria.add(new Criteria().orOperator(
                    Criteria.where("title").regex(pattern),
                    Criteria.where("author").regex(pattern),
                    Criteria.where("categoryDetails.name").regex(pattern)));
        }
        operations.add(Aggregation.match(new Criteria().andOperator(criteria.toArray(new Criteria[0]))));

        List<AggregationOperation> countOps = new ArrayList<>(operations);
        countOps.add(Aggregation.count().as("n"));
        Document countRow = mongoTemplate.aggregate(Aggregation.newAggregation(countOps), "book", Document.class)
                .getUniqueMappedResult();
        long total = countRow == null ? 0 : ((Number) countRow.get("n")).longValue();

        operations.add(Aggregation.sort(Sort.Direction.DESC, "createdAt"));
        operations.add(Aggregation.skip((long) queryDTO.page() * queryDTO.pageSize()));
        operations.add(Aggregation.limit(queryDTO.pageSize()));
        if (searching) operations.add(Aggregation.project().andExclude("categoryDetails"));

        List<Book> books = mongoTemplate.aggregate(Aggregation.newAggregation(operations), "book", Book.class)
                .getMappedResults();
        return PageResponse.of(books, queryDTO.page(), queryDTO.pageSize(), total);
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
    public Book viewed(String id, String viewerKey) {
        Book book = findById(id);
        if (book.getStatus() != Constants.BookStatus.ACTIVE) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Book not found");
        }
        String key = "view:" + id + ":" + viewerKey;
        if (!redisService.exists(key)) {
            redisService.save(key, 1, 1, TimeUnit.DAYS);
            book.setViewCount((book.getViewCount() == null ? 0 : book.getViewCount()) + 1);
            book = bookRepository.save(book);
        }
        return book;
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

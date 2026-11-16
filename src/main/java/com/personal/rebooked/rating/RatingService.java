package com.personal.rebooked.rating;

import com.personal.rebooked.book.models.Book;
import com.personal.rebooked.book.repositories.BookRepository;
import com.personal.rebooked.conversation.models.Conversation;
import com.personal.rebooked.conversation.repositories.ConversationRepository;
import com.personal.rebooked.exception.ApiException;
import com.personal.rebooked.rating.dto.RatePayload;
import com.personal.rebooked.rating.dto.RatingPage;
import com.personal.rebooked.rating.dto.RatingView;
import com.personal.rebooked.user.models.Profile;
import com.personal.rebooked.user.models.User;
import com.personal.rebooked.user.repositories.UserRepository;
import com.personal.rebooked.utils.Constants;
import lombok.RequiredArgsConstructor;
import org.bson.Document;
import org.bson.types.ObjectId;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.FindAndModifyOptions;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

import static com.mongodb.client.model.Filters.eq;
import static org.springframework.data.mongodb.core.query.Criteria.where;
import static org.springframework.data.mongodb.core.query.Query.query;

/**
 * Buyers rate sellers, one rating per sale. The seller's running summary on their profile
 * (sellerRating) is updated atomically as each rating is saved; reads never recalculate it.
 */
@Service
@RequiredArgsConstructor
public class RatingService {
    public static final int COMMENT_MAX = 300;
    private static final int DEFAULT_PAGE_SIZE = 10;
    private static final int MAX_PAGE_SIZE = 50;

    private final MongoTemplate mongoTemplate;
    private final BookRepository bookRepository;
    private final UserRepository userRepository;
    private final ConversationRepository conversationRepository;

    /** GET /book/{id}/rating */
    public RatingView get(String me, String bookId) {
        Book book = bookOr404(bookId);
        SellerRating mine = mongoTemplate.findOne(query(where("bookId").is(bookId).and("buyerId").is(me)), SellerRating.class);
        return view(me, book, mine);
    }

    /** PUT /book/{id}/rating: creates or edits the caller's rating of this sale. */
    public RatingView rate(String me, String bookId, RatePayload payload) {
        Book book = bookOr404(bookId);
        if (!canRate(me, book)) {
            throw new ApiException(HttpStatus.FORBIDDEN, "Only the buyer of this book can rate the seller");
        }
        Map<String, String> errors = new LinkedHashMap<>();
        Integer stars = payload == null ? null : payload.stars();
        if (stars == null || stars < 1 || stars > 5) errors.put("stars", "Choose from 1 to 5 stars");
        String comment = payload == null || payload.comment() == null ? null : payload.comment().trim();
        if (comment != null && comment.isEmpty()) comment = null;
        if (comment != null && comment.length() > COMMENT_MAX) {
            errors.put("comment", "Keep your comment to " + COMMENT_MAX + " characters");
        }
        if (!errors.isEmpty()) throw new ApiException(HttpStatus.BAD_REQUEST, "Validation failed", errors);

        String sellerId = book.getUser().getId();
        Date now = new Date();
        Update update = new Update().set("stars", stars).set("updatedAt", now)
                .setOnInsert("sellerId", sellerId).setOnInsert("createdAt", now);
        if (comment != null) update.set("comment", comment); else update.unset("comment");
        Query byBuyer = query(where("bookId").is(bookId).and("buyerId").is(me));

        // findAndModify returns the rating as it was, so concurrent edits each apply their own difference
        SellerRating previous;
        try {
            previous = mongoTemplate.findAndModify(byBuyer, update, FindAndModifyOptions.options().upsert(true).returnNew(false),
                    SellerRating.class);
        } catch (DuplicateKeyException e) {
            // Two first ratings raced; the other one inserted, so this is now an edit
            previous = mongoTemplate.findAndModify(byBuyer, update, FindAndModifyOptions.options().returnNew(false),
                    SellerRating.class);
        }
        updateSummary(book.getUser(), previous == null ? null : previous.getStars(), stars);
        return view(me, book, mongoTemplate.findOne(byBuyer, SellerRating.class));
    }

    /** GET /user/{id}/ratings: public, newest first. */
    public RatingPage list(String sellerId, Integer page, Integer pageSize) {
        int p = page == null || page < 0 ? 0 : page;
        int size = pageSize == null || pageSize < 1 ? DEFAULT_PAGE_SIZE : Math.min(pageSize, MAX_PAGE_SIZE);
        Query bySeller = query(where("sellerId").is(sellerId));
        long total = mongoTemplate.count(bySeller, SellerRating.class);
        List<SellerRating> ratings = mongoTemplate.find(Query.of(bySeller)
                .with(Sort.by(Sort.Direction.DESC, "createdAt")).skip((long) p * size).limit(size), SellerRating.class);

        Map<String, User> buyers = userRepository.findAllById(ratings.stream().map(SellerRating::getBuyerId)
                        .filter(ObjectId::isValid).distinct().toList()).stream()
                .collect(Collectors.toMap(User::getId, Function.identity()));
        // Title and slug only: no DBRefs are loaded
        Query bookQuery = query(where("_id").in(ratings.stream().map(SellerRating::getBookId)
                .filter(ObjectId::isValid).map(ObjectId::new).distinct().toList()));
        bookQuery.fields().include("title", "slug");
        Map<String, Book> books = mongoTemplate.find(bookQuery, Book.class).stream()
                .collect(Collectors.toMap(Book::getId, Function.identity()));

        List<RatingPage.Review> content = ratings.stream().map(r -> {
            User buyer = buyers.get(r.getBuyerId());
            Profile profile = buyer != null ? buyer.getProfile() : null;
            Book book = books.get(r.getBookId());
            return new RatingPage.Review(r.getId(), r.getStars(), r.getComment(), r.getCreatedAt(),
                    new RatingPage.Buyer(profile != null ? profile.getUserName() : null,
                            profile != null ? profile.getProfilePictureUrl() : null),
                    book == null ? null : new RatingPage.Book(book.getId(), book.getTitle(), book.getSlug()));
        }).toList();
        int totalPages = (int) ((total + size - 1) / size);
        return new RatingPage(content, p, size, total, totalPages);
    }

    public boolean hasRated(String buyerId, String bookId) {
        return mongoTemplate.exists(query(where("bookId").is(bookId).and("buyerId").is(buyerId)), SellerRating.class);
    }

    // ---------------------------------------------------------------- helpers

    private static boolean canRate(String me, Book book) {
        return book.getStatus() == Constants.BookStatus.SOLD && me.equals(book.getBuyerId()) && book.getUser() != null;
    }

    /**
     * One atomic pipeline update on the seller's profile:
     * new rating: count +1, total +stars, stars.n +1; edit: total + (new − old), stars.old −1, stars.new +1;
     * then average = round(total / count, 1).
     */
    private void updateSummary(User seller, Integer oldStars, int newStars) {
        if (seller == null || seller.getProfile() == null || seller.getProfile().getId() == null) return;
        if (oldStars != null && oldStars == newStars) return; // only the comment changed

        Document inc = new Document();
        if (oldStars == null) {
            inc.put("count", 1);
            inc.put("total", newStars);
        } else {
            inc.put("total", newStars - oldStars);
            inc.put("stars." + oldStars, -1);
        }
        inc.merge("stars." + newStars, 1, (a, b) -> (Integer) a + (Integer) b);

        Document set = new Document();
        inc.forEach((field, delta) -> set.put("sellerRating." + field,
                new Document("$add", List.of(new Document("$ifNull", Arrays.asList("$sellerRating." + field, 0)), delta))));
        Document average = new Document("sellerRating.average", new Document("$cond", List.of(
                new Document("$gt", List.of("$sellerRating.count", 0)),
                new Document("$round", List.of(new Document("$divide", List.of("$sellerRating.total", "$sellerRating.count")), 1)),
                0)));
        mongoTemplate.getCollection(mongoTemplate.getCollectionName(Profile.class)).updateOne(
                eq("_id", new ObjectId(seller.getProfile().getId())),
                List.of(new Document("$set", set), new Document("$set", average)));
    }

    private RatingView view(String me, Book book, SellerRating mine) {
        User seller = book.getUser();
        Profile profile = seller != null ? seller.getProfile() : null;
        String conversationId = null;
        if (seller != null && !seller.getId().equals(me)) {
            conversationId = conversationRepository.findByPairKey(Conversation.pairKey(me, seller.getId()))
                    .filter(c -> c.book(book.getId()).isPresent()).map(Conversation::getId).orElse(null);
        }
        return new RatingView(
                canRate(me, book),
                mine == null ? null : new RatingView.Mine(mine.getStars(), mine.getComment(), mine.getUpdatedAt()),
                seller == null ? null : new RatingView.Seller(seller.getId(),
                        profile != null ? profile.getUserName() : null, profile != null ? profile.getProfilePictureUrl() : null),
                new RatingView.Book(book.getId(), book.getTitle(), book.getSlug(),
                        book.getCoverImage() != null ? book.getCoverImage().getUrl() : null),
                conversationId);
    }

    private Book bookOr404(String bookId) {
        if (bookId == null || !ObjectId.isValid(bookId)) throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Book not found");
        return bookRepository.findById(bookId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Book not found"));
    }
}

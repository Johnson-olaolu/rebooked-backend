package com.personal.rebooked.homepage.sections;

import com.fasterxml.jackson.core.type.TypeReference;
import com.personal.rebooked.book.BookService;
import com.personal.rebooked.book.models.Book;
import com.personal.rebooked.homepage.HomepageCache;
import com.personal.rebooked.homepage.dto.ReviewCard;
import com.personal.rebooked.rating.SellerRating;
import com.personal.rebooked.user.models.Profile;
import com.personal.rebooked.user.models.User;
import com.personal.rebooked.user.repositories.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Duration;
import java.util.*;

import static org.springframework.data.mongodb.core.query.Criteria.where;

/**
 * §24g readers are saying: 5 random ratings from the last 7 days (30 if fewer than 3 qualify) with 4+ stars and a
 * comment of 20+ characters, at most one per seller. Hourly. The reviewer is only a username and avatar.
 */
@Component
@RequiredArgsConstructor
public class ReviewsSection {
    public static final String KEY = "homepage:reviews";
    static final Duration TTL = Duration.ofHours(1);
    static final int COUNT = 5;
    static final int MIN = 3;
    static final int MIN_STARS = 4;
    static final int MIN_COMMENT = 20;
    static final Duration RECENT = Duration.ofDays(7);
    static final Duration WIDER = Duration.ofDays(30);

    private final HomepageCache cache;
    private final MongoTemplate mongoTemplate;
    private final BookService bookService;
    private final UserRepository userRepository;
    private final Clock clock;
    private final Random random = new Random();

    public List<ReviewCard> read() {
        List<String> ids = cache.get(KEY, new TypeReference<List<String>>() {}).orElseGet(this::build);
        if (ids.isEmpty()) return List.of();
        Map<String, SellerRating> ratings = new HashMap<>();
        for (SellerRating r : mongoTemplate.find(Query.query(where("_id").in(ids)), SellerRating.class)) ratings.put(r.getId(), r);

        List<SellerRating> ordered = ids.stream().map(ratings::get).filter(Objects::nonNull).toList();
        Map<String, Book> books = new HashMap<>();
        for (Book b : bookService.booksInOrder(ordered.stream().map(SellerRating::getBookId).toList())) books.put(b.getId(), b);
        Map<String, User> buyers = new HashMap<>();
        for (User u : userRepository.findAllById(ordered.stream().map(SellerRating::getBuyerId).filter(Objects::nonNull).toList())) {
            buyers.put(u.getId(), u);
        }

        List<ReviewCard> cards = new ArrayList<>();
        for (SellerRating r : ordered) {
            Book book = books.get(r.getBookId());
            if (book == null) continue; // the book was removed
            User buyer = buyers.get(r.getBuyerId());
            Profile profile = buyer == null || buyer.isDeleted() ? null : buyer.getProfile();
            cards.add(new ReviewCard(r.getStars(), r.getComment().trim(), r.getCreatedAt(),
                    new ReviewCard.Reviewer(profile == null ? null : profile.getUserName(),
                            profile == null ? null : profile.getProfilePictureUrl()),
                    new ReviewCard.ReviewBook(book.getId(), book.getTitle(), book.getCoverImage())));
        }
        return cards;
    }

    public List<String> build() {
        List<SellerRating> eligible = eligible(since(RECENT));
        if (eligible.size() < MIN) eligible = eligible(since(WIDER));
        List<String> ids = pick(eligible, COUNT, random).stream().map(SellerRating::getId).toList();
        cache.put(KEY, ids, TTL);
        return ids;
    }

    private List<SellerRating> since(Duration window) {
        Date from = Date.from(clock.instant().minus(window));
        return mongoTemplate.find(Query.query(where("createdAt").gte(from).and("stars").gte(MIN_STARS)
                .and("comment").ne(null)), SellerRating.class);
    }

    static List<SellerRating> eligible(List<SellerRating> ratings) {
        return ratings.stream()
                .filter(r -> r.getStars() >= MIN_STARS)
                .filter(r -> r.getComment() != null && r.getComment().trim().length() >= MIN_COMMENT)
                .toList();
    }

    /** {@code count} at random, at most one per seller. */
    static List<SellerRating> pick(List<SellerRating> eligible, int count, Random random) {
        List<SellerRating> shuffled = new ArrayList<>(eligible);
        Collections.shuffle(shuffled, random);
        Set<String> sellers = new HashSet<>();
        List<SellerRating> picked = new ArrayList<>();
        for (SellerRating r : shuffled) {
            if (picked.size() >= count) break;
            if (sellers.add(String.valueOf(r.getSellerId()))) picked.add(r);
        }
        return picked;
    }
}

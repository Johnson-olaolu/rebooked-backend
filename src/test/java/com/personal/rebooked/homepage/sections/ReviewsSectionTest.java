package com.personal.rebooked.homepage.sections;

import com.personal.rebooked.rating.SellerRating;
import org.junit.jupiter.api.Test;

import java.util.*;

import static org.assertj.core.api.Assertions.assertThat;

class ReviewsSectionTest {
    private static SellerRating rating(String id, String seller, int stars, String comment) {
        SellerRating r = new SellerRating();
        r.setId(id);
        r.setSellerId(seller);
        r.setStars(stars);
        r.setComment(comment);
        return r;
    }

    private static final String LONG = "Exactly as described, met at the gate on time.";

    @Test
    void onlyFourStarsAndUpWithACommentOf20Characters() {
        List<SellerRating> eligible = ReviewsSection.eligible(List.of(
                rating("1", "s1", 5, LONG),
                rating("2", "s2", 3, LONG),
                rating("3", "s3", 4, "Too short"),
                rating("4", "s4", 4, null),
                rating("5", "s5", 4, "   " + "a".repeat(19) + "   ")));
        assertThat(eligible).extracting(SellerRating::getId).containsExactly("1");
    }

    @Test
    void picksAtMostOnePerSeller() {
        List<SellerRating> ratings = new ArrayList<>();
        for (int i = 0; i < 6; i++) ratings.add(rating("a" + i, "busy-seller", 5, LONG));
        ratings.add(rating("b", "other", 5, LONG));
        List<SellerRating> picked = ReviewsSection.pick(ratings, 5, new Random(1));
        assertThat(picked).hasSize(2);
        assertThat(picked).extracting(SellerRating::getSellerId).containsExactlyInAnyOrder("busy-seller", "other");
    }

    @Test
    void picksFiveAtMost() {
        List<SellerRating> ratings = new ArrayList<>();
        for (int i = 0; i < 9; i++) ratings.add(rating("r" + i, "s" + i, 5, LONG));
        assertThat(ReviewsSection.pick(ratings, 5, new Random(7))).hasSize(5).doesNotHaveDuplicates();
    }
}

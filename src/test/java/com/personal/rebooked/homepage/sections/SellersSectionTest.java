package com.personal.rebooked.homepage.sections;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class SellersSectionTest {
    @Test
    void ordersByAverageThenCountThenSoldAndNeedsThreeRatings() {
        List<SellersSection.Candidate> ranked = SellersSection.rank(List.of(
                new SellersSection.Candidate("few", 5.0, 2, 10),
                new SellersSection.Candidate("good", 4.6, 10, 1),
                new SellersSection.Candidate("best-busy", 4.9, 12, 3),
                new SellersSection.Candidate("best-quiet", 4.9, 4, 30),
                new SellersSection.Candidate("best-busy-sold", 4.9, 12, 8)));
        assertThat(ranked).extracting(SellersSection.Candidate::userId)
                .containsExactly("best-busy-sold", "best-busy", "best-quiet", "good");
    }
}

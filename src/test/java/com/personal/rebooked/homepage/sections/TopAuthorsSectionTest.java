package com.personal.rebooked.homepage.sections;

import com.personal.rebooked.homepage.dto.ScoredAuthor;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class TopAuthorsSectionTest {

    @Test
    void scoreAddsViewsFiveTimesSalesAndTwiceListings() {
        Map<String, Long> scores = TopAuthorsSection.score(
                Map.of("book1", 10d, "book2", 4d),
                Map.of("book1", List.of("ada", "ben"), "book2", List.of("ada")),
                List.of(List.of("ben")),               // one of ben's books sold this week
                List.of(List.of("ada"), List.of("cy"))); // ada and cy each listed one
        assertThat(scores).containsEntry("ada", 10 + 4 + 2L)
                .containsEntry("ben", 10 + 5L)
                .containsEntry("cy", 2L);
    }

    @Test
    void rankKeepsOnlyQualifyingAuthorsAndBreaksTiesByAvailableCount() {
        List<ScoredAuthor> ranked = TopAuthorsSection.rank(
                Map.of("ada", 20L, "ben", 20L, "cy", 50L, "merged", 99L),
                Map.of("ada", 1L, "ben", 5L, "cy", 2L)); // "merged" has no ACTIVE book / is MERGED
        assertThat(ranked).extracting(ScoredAuthor::authorId).containsExactly("cy", "ben", "ada");
        assertThat(ranked.get(0).weekScore()).isEqualTo(50);
    }
}

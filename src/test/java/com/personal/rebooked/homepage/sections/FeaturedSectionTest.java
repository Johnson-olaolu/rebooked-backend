package com.personal.rebooked.homepage.sections;

import com.personal.rebooked.book.models.Book;
import com.personal.rebooked.file.models.File;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.*;

import static org.assertj.core.api.Assertions.assertThat;

class FeaturedSectionTest {
    private static final LocalDate WEEK = LocalDate.parse("2026-11-16");
    private static final LocalDate CUTOFF = WEEK.minusWeeks(FeaturedSection.CATEGORY_HISTORY_WEEKS);

    @Test
    void neverRepeatsACategoryFeaturedInTheLastEightWeeks() {
        Map<String, LocalDate> last = Map.of("fiction", WEEK.minusWeeks(2), "poetry", WEEK.minusWeeks(9));
        Map<String, Double> views = Map.of("fiction", 1000d, "poetry", 5d, "history", 1d);
        for (int seed = 0; seed < 20; seed++) {
            String pick = FeaturedSection.pickCategory(List.of("fiction", "poetry", "history"), last, CUTOFF, views, new Random(seed));
            assertThat(pick).isIn("poetry", "history");
        }
    }

    @Test
    void picksFromTheTopThreeByViews() {
        List<String> eligible = List.of("a", "b", "c", "d", "e");
        Map<String, Double> views = Map.of("a", 1d, "b", 50d, "c", 40d, "d", 30d, "e", 20d);
        Set<String> seen = new HashSet<>();
        for (int seed = 0; seed < 50; seed++) {
            seen.add(FeaturedSection.pickCategory(eligible, Map.of(), CUTOFF, views, new Random(seed)));
        }
        assertThat(seen).containsExactlyInAnyOrder("b", "c", "d");
    }

    @Test
    void whenEveryCategoryWasRecentPicksTheOneFeaturedLongestAgo() {
        Map<String, LocalDate> last = Map.of("a", WEEK.minusWeeks(1), "b", WEEK.minusWeeks(6), "c", WEEK.minusWeeks(3));
        assertThat(FeaturedSection.pickCategory(List.of("a", "b", "c"), last, CUTOFF, Map.of(), new Random())).isEqualTo("b");
    }

    @Test
    void noEligibleCategoryMeansNone() {
        assertThat(FeaturedSection.pickCategory(List.of(), Map.of(), CUTOFF, Map.of(), new Random())).isNull();
    }

    @Test
    void booksWithACoverComeFirstThenViewsThenNewest() {
        Book noCover = book("no-cover", false, 5);
        Book popular = book("popular", true, 1);
        Book fresh = book("fresh", true, 9);
        Book older = book("older", true, 2);
        Map<String, Double> views = Map.of("popular", 100d, "no-cover", 1000d);
        List<Book> books = new ArrayList<>(List.of(noCover, older, fresh, popular));
        books.sort(FeaturedSection.bookOrder(views));
        assertThat(books).extracting(Book::getId).containsExactly("popular", "fresh", "older", "no-cover");
    }

    private static Book book(String id, boolean cover, int day) {
        Book b = new Book();
        b.setId(id);
        if (cover) b.setCoverImage(new File());
        b.setCreatedAt(new Date(day * 86_400_000L));
        return b;
    }
}

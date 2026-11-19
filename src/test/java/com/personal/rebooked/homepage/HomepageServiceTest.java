package com.personal.rebooked.homepage;

import com.personal.rebooked.book.models.Book;
import com.personal.rebooked.homepage.dto.HomepageBundle;
import com.personal.rebooked.homepage.dto.HomepageStats;
import com.personal.rebooked.homepage.sections.*;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/** One broken section must not break GET /homepage (§24b). */
class HomepageServiceTest {
    private final StatsSection stats = mock(StatsSection.class);
    private final TrendingSection trending = mock(TrendingSection.class);
    private final NewArrivalsSection newArrivals = mock(NewArrivalsSection.class);
    private final CategoriesSection categories = mock(CategoriesSection.class);
    private final FeaturedSection featured = mock(FeaturedSection.class);
    private final TopAuthorsSection topAuthors = mock(TopAuthorsSection.class);
    private final AiPicksSection aiPicks = mock(AiPicksSection.class);
    private final ReviewsSection reviews = mock(ReviewsSection.class);
    private final SellersSection sellers = mock(SellersSection.class);
    private final HomepageService service = new HomepageService(stats, trending, newArrivals, categories, featured,
            topAuthors, aiPicks, reviews, sellers,
            Clock.fixed(Instant.parse("2026-11-18T09:15:00Z"), ZoneOffset.UTC), new SimpleMeterRegistry());

    @Test
    void aFailingSectionComesBackEmptyAndTheRestIsServed() {
        Book book = new Book();
        book.setId("b1");
        when(stats.read()).thenThrow(new IllegalStateException("Redis exploded"));
        when(trending.read()).thenReturn(List.of(book));
        when(featured.read()).thenThrow(new RuntimeException("boom"));
        when(newArrivals.read()).thenReturn(List.of(book));

        HomepageBundle bundle = service.bundle();

        assertThat(bundle.stats()).isNull();
        assertThat(bundle.featured()).isNull();
        assertThat(bundle.hero()).isEmpty();
        assertThat(bundle.trending()).containsExactly(book);
        assertThat(bundle.newArrivals()).containsExactly(book);
        assertThat(bundle.weekOf()).hasToString("2026-11-16");
        assertThat(bundle.reviews()).isEmpty();
    }

    @Test
    void statsComeFromTheSnapshot() {
        HomepageStats snapshot = new HomepageStats(10, 2, 30, 4, 5, null);
        when(stats.read()).thenReturn(snapshot);
        assertThat(service.stats()).isSameAs(snapshot);
    }
}

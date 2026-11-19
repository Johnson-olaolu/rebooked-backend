package com.personal.rebooked.homepage;

import com.personal.rebooked.exception.ApiException;
import com.personal.rebooked.homepage.dto.HomepageBundle;
import com.personal.rebooked.homepage.dto.HomepageStats;
import com.personal.rebooked.homepage.sections.*;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.util.*;
import java.util.function.Supplier;

/**
 * Assembles GET /homepage from the section snapshots (§24b). Every section is read in its own try/catch:
 * a failing section comes back null or [] and the rest of the page is still served.
 */
@Slf4j
@Service
public class HomepageService {
    /** Over this, a request is logged; the p95 alert reads the homepage.request timer. */
    static final long SLOW_MS = 300;
    public static final List<String> SECTIONS = List.of("stats", "trending", "featured", "ai-picks", "top-authors",
            "reviews", "sellers", "new", "categories");

    private final StatsSection stats;
    private final TrendingSection trending;
    private final NewArrivalsSection newArrivals;
    private final CategoriesSection categories;
    private final FeaturedSection featured;
    private final TopAuthorsSection topAuthors;
    private final AiPicksSection aiPicks;
    private final ReviewsSection reviews;
    private final SellersSection sellers;
    private final Clock clock;
    private final Timer timer;

    public HomepageService(StatsSection stats, TrendingSection trending, NewArrivalsSection newArrivals,
                           CategoriesSection categories, FeaturedSection featured, TopAuthorsSection topAuthors,
                           AiPicksSection aiPicks, ReviewsSection reviews, SellersSection sellers, Clock clock,
                           MeterRegistry meterRegistry) {
        this.stats = stats;
        this.trending = trending;
        this.newArrivals = newArrivals;
        this.categories = categories;
        this.featured = featured;
        this.topAuthors = topAuthors;
        this.aiPicks = aiPicks;
        this.reviews = reviews;
        this.sellers = sellers;
        this.clock = clock;
        this.timer = Timer.builder("homepage.request").description("GET /homepage")
                .publishPercentiles(0.95).register(meterRegistry);
    }

    public HomepageBundle bundle() {
        long started = System.nanoTime();
        FeaturedSection.Resolved feature = section("featured", featured::read, null);
        HomepageBundle bundle = new HomepageBundle(
                clock.instant(),
                HomepageWeek.current(clock),
                feature == null ? List.of() : feature.hero(),
                section("stats", stats::read, null),
                section("categories", categories::read, List.of()),
                feature == null ? null : feature.featured(),
                section("aiPicks", aiPicks::read, List.of()),
                section("trending", trending::read, List.of()),
                section("newArrivals", newArrivals::read, List.of()),
                section("topAuthors", topAuthors::read, List.of()),
                section("reviews", reviews::read, List.of()),
                section("sellers", sellers::read, List.of()));
        long ms = (System.nanoTime() - started) / 1_000_000;
        timer.record(java.time.Duration.ofMillis(ms));
        if (ms > SLOW_MS) log.warn("GET /homepage took {} ms", ms);
        return bundle;
    }

    public HomepageStats stats() {
        return stats.read();
    }

    /** POST /admin/homepage/refresh/{section}: rebuilds now and returns what was rebuilt. */
    public Map<String, Object> refresh(String section) {
        String name = section == null ? "" : section.trim().toLowerCase(Locale.ROOT);
        List<String> targets = name.equals("all") ? SECTIONS : List.of(name);
        if (!name.equals("all") && !SECTIONS.contains(name)) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Validation failed",
                    Map.of("section", "One of " + String.join(", ", SECTIONS) + " or all"));
        }
        Map<String, Object> results = new LinkedHashMap<>();
        for (String target : targets) {
            long started = System.currentTimeMillis();
            try {
                Object outcome = rebuild(target);
                results.put(target, Map.of("ok", !Boolean.FALSE.equals(outcome), "ms", System.currentTimeMillis() - started));
            } catch (Exception e) {
                log.error("Rebuilding homepage section {} failed", target, e);
                results.put(target, Map.of("ok", false, "ms", System.currentTimeMillis() - started, "error", e.toString()));
            }
        }
        return results;
    }

    private Object rebuild(String section) {
        return switch (section) {
            case "stats" -> stats.build();
            case "trending" -> trending.build();
            case "featured" -> featured.regenerateCurrent();
            case "ai-picks" -> aiPicks.build();
            case "top-authors" -> topAuthors.build();
            case "reviews" -> reviews.build();
            case "sellers" -> sellers.build();
            case "new" -> newArrivals.build();
            case "categories" -> categories.build();
            default -> throw new IllegalArgumentException(section);
        };
    }

    private static <T> T section(String name, Supplier<T> read, T fallback) {
        try {
            T value = read.get();
            return value == null ? fallback : value;
        } catch (Exception e) {
            log.error("Homepage section {} failed; serving the rest of the page", name, e);
            return fallback;
        }
    }
}

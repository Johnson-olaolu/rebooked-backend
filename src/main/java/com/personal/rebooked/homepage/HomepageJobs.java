package com.personal.rebooked.homepage;

import com.personal.rebooked.homepage.sections.*;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.function.Supplier;

/**
 * The homepage snapshot builders (§24h). Every schedule is a property so the end-to-end test can run them quickly;
 * crons use Africa/Lagos. Each run logs its duration and outcome. Missing snapshots are warmed after startup.
 */
@Slf4j
@Component
public class HomepageJobs {
    static final String AI_WARM_UP_KEY = "homepage:ai-picks:warm-up";

    private final StatsSection stats;
    private final TrendingSection trending;
    private final NewArrivalsSection newArrivals;
    private final CategoriesSection categories;
    private final FeaturedSection featured;
    private final TopAuthorsSection topAuthors;
    private final AiPicksSection aiPicks;
    private final ReviewsSection reviews;
    private final SellersSection sellers;
    private final HomepageCache cache;
    private final ThreadPoolTaskScheduler scheduler;
    private final Clock clock;

    public HomepageJobs(StatsSection stats, TrendingSection trending, NewArrivalsSection newArrivals,
                        CategoriesSection categories, FeaturedSection featured, TopAuthorsSection topAuthors,
                        AiPicksSection aiPicks, ReviewsSection reviews, SellersSection sellers, HomepageCache cache,
                        @Qualifier("taskScheduler") ThreadPoolTaskScheduler scheduler, Clock clock) {
        this.stats = stats;
        this.trending = trending;
        this.newArrivals = newArrivals;
        this.categories = categories;
        this.featured = featured;
        this.topAuthors = topAuthors;
        this.aiPicks = aiPicks;
        this.reviews = reviews;
        this.sellers = sellers;
        this.cache = cache;
        this.scheduler = scheduler;
        this.clock = clock;
    }

    @Scheduled(fixedDelayString = "${homepage.stats-interval:PT15M}", initialDelayString = "${homepage.stats-interval:PT15M}")
    public void stats() {
        run("stats", stats::build);
    }

    /** Rebuilds views:week too. */
    @Scheduled(fixedDelayString = "${homepage.trending-interval:PT1H}", initialDelayString = "${homepage.trending-interval:PT1H}")
    public void trending() {
        run("trending", trending::build);
    }

    @Scheduled(fixedDelayString = "${homepage.reviews-interval:PT1H}", initialDelayString = "${homepage.reviews-interval:PT1H}")
    public void reviews() {
        run("reviews", reviews::build);
    }

    @Scheduled(cron = "${homepage.daily-cron:0 30 2 * * *}", zone = "Africa/Lagos")
    public void daily() {
        run("sellers", sellers::build);
        run("top-authors", topAuthors::build);
    }

    @Scheduled(cron = "${homepage.ai-picks-cron:0 0 3 * * *}", zone = "Africa/Lagos")
    public void aiPicks() {
        run("ai-picks", aiPicks::build);
    }

    /** Monday: this week's top authors first, since the featured author is chosen from them. */
    @Scheduled(cron = "${homepage.weekly-cron:0 0 6 * * MON}", zone = "Africa/Lagos")
    public void weekly() {
        run("top-authors", topAuthors::build);
        run("featured", featured::ensureCurrent);
    }

    /** A fresh deploy or a flushed Redis shouldn't serve an empty page: build what's missing, off the startup thread. */
    @EventListener(ApplicationReadyEvent.class)
    public void warmUp() {
        scheduler.schedule(() -> {
            var week = HomepageWeek.current(clock);
            if (!cache.exists(StatsSection.KEY)) run("stats", stats::build);
            if (!cache.exists(TrendingSection.KEY)) run("trending", trending::build);
            if (!cache.exists(NewArrivalsSection.KEY)) run("new", newArrivals::build);
            if (!cache.exists(CategoriesSection.KEY)) run("categories", categories::build);
            if (!cache.exists(TopAuthorsSection.key(week))) run("top-authors", topAuthors::build);
            if (!cache.exists(FeaturedSection.key(week))) run("featured", featured::ensureCurrent);
            if (!cache.exists(ReviewsSection.KEY)) run("reviews", reviews::build);
            if (!cache.exists(SellersSection.KEY)) run("sellers", sellers::build);
            // At most one model call an hour from restarts (DevTools reloads, crash loops); the daily job is the real source
            if (!aiPicks.exists() && cache.claim(AI_WARM_UP_KEY, Duration.ofHours(1))) run("ai-picks", aiPicks::build);
        }, Instant.now());
    }

    private static void run(String job, Supplier<?> body) {
        long started = System.currentTimeMillis();
        try {
            Object outcome = body.get();
            log.info("Homepage job {} {} in {} ms", job, Boolean.FALSE.equals(outcome) ? "kept the previous snapshot" : "done",
                    System.currentTimeMillis() - started);
        } catch (Exception e) {
            log.error("Homepage job {} failed after {} ms", job, System.currentTimeMillis() - started, e);
        }
    }
}

package com.personal.rebooked.homepage;

import java.time.*;
import java.time.temporal.TemporalAdjusters;
import java.util.Date;

/** "This week" on the homepage: the ISO week starting Monday 00:00 Africa/Lagos (§24). */
public final class HomepageWeek {
    public static final ZoneId ZONE = ZoneId.of("Africa/Lagos");

    private HomepageWeek() {}

    /** The Monday of the week containing {@code instant}, in Lagos. */
    public static LocalDate startOf(Instant instant) {
        return instant.atZone(ZONE).toLocalDate().with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
    }

    public static LocalDate current(Clock clock) {
        return startOf(clock.instant());
    }

    /** Monday 00:00 Lagos as an instant. */
    public static Instant startInstant(LocalDate weekStart) {
        return weekStart.atStartOfDay(ZONE).toInstant();
    }

    /** The rolling 7-day window the week's scores use: now minus 7 days. */
    public static Date sevenDaysAgo(Clock clock) {
        return Date.from(clock.instant().minus(Duration.ofDays(7)));
    }

    /** Today and the 6 days before it, in Lagos, newest first: the day buckets that make up views:week. */
    public static LocalDate[] lastSevenDays(Clock clock) {
        LocalDate today = LocalDate.now(clock.withZone(ZONE));
        LocalDate[] days = new LocalDate[7];
        for (int i = 0; i < 7; i++) days[i] = today.minusDays(i);
        return days;
    }

    /** Parses ?week=yyyy-MM-dd to the Monday of that week; null or blank means the current week. */
    public static LocalDate parse(String week, Clock clock) {
        if (week == null || week.isBlank()) return current(clock);
        LocalDate day = LocalDate.parse(week.trim());
        return day.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
    }
}

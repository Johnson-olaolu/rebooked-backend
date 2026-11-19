package com.personal.rebooked.homepage;

import org.junit.jupiter.api.Test;

import java.time.*;

import static org.assertj.core.api.Assertions.assertThat;

class HomepageWeekTest {
    private static Instant lagos(String localDateTime) {
        return LocalDateTime.parse(localDateTime).atZone(HomepageWeek.ZONE).toInstant();
    }

    @Test
    void sundayLateInLagosIsStillThePreviousWeek() {
        // Sunday 23:30 Lagos
        assertThat(HomepageWeek.startOf(lagos("2026-11-22T23:30"))).isEqualTo(LocalDate.parse("2026-11-16"));
    }

    @Test
    void mondayJustAfterMidnightInLagosStartsTheNewWeek() {
        assertThat(HomepageWeek.startOf(lagos("2026-11-23T00:30"))).isEqualTo(LocalDate.parse("2026-11-23"));
    }

    @Test
    void usesLagosNotUtc() {
        // Sunday 23:30 UTC is already Monday 00:30 in Lagos
        Instant sundayUtc = LocalDateTime.parse("2026-11-22T23:30").toInstant(ZoneOffset.UTC);
        assertThat(HomepageWeek.startOf(sundayUtc)).isEqualTo(LocalDate.parse("2026-11-23"));
    }

    @Test
    void weekParamSnapsToItsMonday() {
        Clock clock = Clock.fixed(lagos("2026-11-18T09:00"), ZoneOffset.UTC);
        assertThat(HomepageWeek.parse("2026-11-20", clock)).isEqualTo(LocalDate.parse("2026-11-16"));
        assertThat(HomepageWeek.parse(null, clock)).isEqualTo(LocalDate.parse("2026-11-16"));
    }

    @Test
    void lastSevenDaysEndsToday() {
        Clock clock = Clock.fixed(lagos("2026-11-18T00:10"), ZoneOffset.UTC);
        LocalDate[] days = HomepageWeek.lastSevenDays(clock);
        assertThat(days).hasSize(7);
        assertThat(days[0]).isEqualTo(LocalDate.parse("2026-11-18"));
        assertThat(days[6]).isEqualTo(LocalDate.parse("2026-11-12"));
    }
}

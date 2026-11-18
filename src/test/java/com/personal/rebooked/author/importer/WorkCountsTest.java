package com.personal.rebooked.author.importer;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class WorkCountsTest {

    @Test
    void countsGrowAndSaturateAt127() {
        WorkCounts counts = new WorkCounts();
        for (int i = 0; i < 200; i++) counts.add(42);
        counts.add(5_000_000); // beyond the initial array
        counts.add(5_000_000);

        assertThat(counts.get(42)).isEqualTo(127);
        assertThat(counts.get(5_000_000)).isEqualTo(2);
        assertThat(counts.get(7)).isZero();
        assertThat(counts.get(99_999_999)).isZero();
        assertThat(counts.get(-1)).isZero();
    }
}

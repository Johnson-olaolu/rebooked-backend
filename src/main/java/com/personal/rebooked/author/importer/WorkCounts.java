package com.personal.rebooked.author.importer;

import java.util.Arrays;

/**
 * Works per Open Library author, indexed by the numeric part of the id (OL123A → 123) and saturating at 127. A byte
 * per author: tens of MB for the whole catalogue.
 */
public class WorkCounts {
    public static final int MAX = 127;
    private static final int MAX_INDEX = Integer.MAX_VALUE - 16;

    private byte[] counts = new byte[1 << 20];

    public void add(long olNumber) {
        if (olNumber <= 0 || olNumber > MAX_INDEX) return;
        int i = (int) olNumber;
        if (i >= counts.length) {
            long grown = Math.max(i + 1L, (long) counts.length + (counts.length >> 1));
            counts = Arrays.copyOf(counts, (int) Math.min(grown, MAX_INDEX));
        }
        if (counts[i] < MAX) counts[i]++;
    }

    public int get(long olNumber) {
        if (olNumber <= 0 || olNumber >= counts.length) return 0;
        return counts[(int) olNumber];
    }
}

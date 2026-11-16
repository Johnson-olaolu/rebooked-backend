package com.personal.rebooked.user.models;

import lombok.Data;

import java.util.Map;

/**
 * A seller's running rating, kept on their profile. Only RatingService changes it, with one atomic
 * update per rating saved; it's never recalculated from the ratings collection.
 */
@Data
public class SellerRatingSummary {
    private double average;
    private int count;
    private int total;
    /** "1".."5" → how many ratings gave that many stars. */
    private Map<String, Integer> stars;
}

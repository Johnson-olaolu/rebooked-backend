package com.personal.rebooked.homepage.dto;

/** §24c stats. avgSellerRating is null until there are 10 ratings. */
public record HomepageStats(long activeBooks, long booksSold, long readers, long sellers, long authors,
                            Double avgSellerRating) {
}

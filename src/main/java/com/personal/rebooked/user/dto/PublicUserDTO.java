package com.personal.rebooked.user.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.personal.rebooked.user.models.Address;
import com.personal.rebooked.user.models.Profile;
import com.personal.rebooked.user.models.SellerRatingSummary;
import com.personal.rebooked.user.models.User;

import java.util.Date;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * What anyone may see about a user: no email, phone number, role or precise address.
 * availableCount, soldCount and rating.stars are only filled in on GET /user/{id} (the seller page).
 */
public record PublicUserDTO(String id, String userName, String profilePictureUrl, String city, String state,
                            String bio, Date joinedAt, boolean hasPhone,
                            @JsonInclude(JsonInclude.Include.NON_NULL) Long availableCount,
                            @JsonInclude(JsonInclude.Include.NON_NULL) Long soldCount,
                            Rating rating) {

    /** average and count everywhere; stars (the breakdown) only on GET /user/{id}. */
    public record Rating(double average, int count,
                         @JsonInclude(JsonInclude.Include.NON_NULL) Map<String, Integer> stars) {
        static final Rating NONE = new Rating(0, 0, null);

        static Rating of(SellerRatingSummary summary, boolean withStars) {
            if (summary == null || summary.getCount() <= 0) {
                return withStars ? new Rating(0, 0, emptyStars()) : NONE;
            }
            if (!withStars) return new Rating(summary.getAverage(), summary.getCount(), null);
            Map<String, Integer> stars = emptyStars();
            if (summary.getStars() != null) {
                summary.getStars().forEach((k, v) -> { if (stars.containsKey(k) && v != null) stars.put(k, v); });
            }
            return new Rating(summary.getAverage(), summary.getCount(), stars);
        }

        private static Map<String, Integer> emptyStars() {
            Map<String, Integer> stars = new LinkedHashMap<>();
            for (int i = 1; i <= 5; i++) stars.put(String.valueOf(i), 0);
            return stars;
        }
    }

    public static PublicUserDTO from(User user) {
        if (user == null) return null;
        Profile profile = user.getProfile();
        Address address = profile != null ? profile.getAddress() : null;
        return new PublicUserDTO(
                user.getId(),
                profile != null ? profile.getUserName() : null,
                profile != null ? profile.getProfilePictureUrl() : null,
                address != null ? address.getCity() : null,
                address != null ? address.getState() : null,
                profile != null ? profile.getBio() : null,
                user.getCreatedAt(),
                profile != null && profile.getContactPhone() != null && !profile.getContactPhone().isBlank(),
                null, null,
                Rating.of(profile != null ? profile.getSellerRating() : null, false));
    }

    /** The seller page: book counts and the star breakdown. */
    public PublicUserDTO forSellerPage(User user, long available, long sold) {
        Profile profile = user.getProfile();
        return new PublicUserDTO(id, userName, profilePictureUrl, city, state, bio, joinedAt, hasPhone, available, sold,
                Rating.of(profile != null ? profile.getSellerRating() : null, true));
    }

    /** A user who no longer exists. */
    public static PublicUserDTO deleted(String id) {
        return new PublicUserDTO(id, null, null, null, null, null, null, false, null, null, Rating.NONE);
    }
}

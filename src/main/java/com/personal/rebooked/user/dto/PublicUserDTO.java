package com.personal.rebooked.user.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.personal.rebooked.user.models.Address;
import com.personal.rebooked.user.models.Profile;
import com.personal.rebooked.user.models.User;

import java.util.Date;

/**
 * What anyone may see about a user: no email, phone number, role or precise address.
 * availableCount and soldCount are only filled in on GET /user/{id} (the seller page).
 */
public record PublicUserDTO(String id, String userName, String profilePictureUrl, String city, String state,
                            String bio, Date joinedAt, boolean hasPhone,
                            @JsonInclude(JsonInclude.Include.NON_NULL) Long availableCount,
                            @JsonInclude(JsonInclude.Include.NON_NULL) Long soldCount) {

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
                null, null);
    }

    public PublicUserDTO withCounts(long available, long sold) {
        return new PublicUserDTO(id, userName, profilePictureUrl, city, state, bio, joinedAt, hasPhone, available, sold);
    }
}

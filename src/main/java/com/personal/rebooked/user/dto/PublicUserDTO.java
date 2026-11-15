package com.personal.rebooked.user.dto;

import com.personal.rebooked.user.models.Address;
import com.personal.rebooked.user.models.Profile;
import com.personal.rebooked.user.models.User;

/** What anyone may see about a user: no email, phone, role or precise address. */
public record PublicUserDTO(String id, String userName, String profilePictureUrl, String city, String state) {

    public static PublicUserDTO from(User user) {
        if (user == null) return null;
        Profile profile = user.getProfile();
        Address address = profile != null ? profile.getAddress() : null;
        return new PublicUserDTO(
                user.getId(),
                profile != null ? profile.getUserName() : null,
                profile != null ? profile.getProfilePictureUrl() : null,
                address != null ? address.getCity() : null,
                address != null ? address.getState() : null);
    }
}

package com.personal.rebooked.user.dto;

public record UpdateProfileDTO(
        String userName,
        String bio,
        String contactEmail,
        String contactPhone,
        String profilePictureId,
        AddressDTO address
        ) {
}

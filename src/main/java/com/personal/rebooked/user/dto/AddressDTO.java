package com.personal.rebooked.user.dto;

/**
 * Address part of PATCH /user/{id}/profile. Every field is optional; sent fields are merged into the stored address.
 * latitude and longitude must be sent together.
 */
public record AddressDTO(
        String country,
        String state,
        String city,
        String addressLine,
        String formattedAddress,
        String placeId,
        Double latitude,
        Double longitude
) {
}

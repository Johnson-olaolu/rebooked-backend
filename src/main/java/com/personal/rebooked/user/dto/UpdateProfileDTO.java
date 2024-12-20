package com.personal.rebooked.user.dto;
import com.personal.rebooked.user.models.Address;
import jakarta.validation.constraints.Email;

public record UpdateProfileDTO(
        String userName,
        String bio,
        @Email String contactEmail,
        String contactPhone,
        String profilePictureId,
        Address address
        ) {
}

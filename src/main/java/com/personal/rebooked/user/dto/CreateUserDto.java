package com.personal.rebooked.user.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

public record CreateUserDto(
        @Email @NotBlank String email,
        @NotBlank String fullName,
        @NotBlank  @Pattern(regexp = "^(?=.*[0-9])(?=.*[a-z])(?=.*[A-Z])(?=.*[@#$%^&+=])(?=\\S+$).{8,}$") String password,
        @NotBlank boolean isVerified,
        String role,
        String profilePictureUrl
) {
    public  CreateUserDto ( String email, String fullName, String password) {
        this (email, fullName, password, false, "user", null);
    }
}

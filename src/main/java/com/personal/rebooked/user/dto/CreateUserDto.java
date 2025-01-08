package com.personal.rebooked.user.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;

public record CreateUserDto(
        @Email @NotNull String email,
        @NotNull String fullName,
        @NotNull @Pattern(regexp = "^(?=.*[0-9])(?=.*[a-z])(?=.*[A-Z])(?=.*[_@#$%^&+=])(?=\\S+$).{8,}$") String password,
        String roleName,
        boolean isVerified,
        String profilePictureUrl
) {
    public CreateUserDto {
        if (roleName == null || roleName.isEmpty()) {
            roleName = "user";
        }
    }
    public  CreateUserDto ( String email, String fullName, String password) {
        this (email, fullName, password, "user", false, null);
    }
}

package com.personal.rebooked.user.dto;

import jakarta.validation.constraints.Email;

public record UpdateUserDTO(
        @Email  String email,
        String fullName
) {
}

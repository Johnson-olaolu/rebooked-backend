package com.personal.rebooked.user.dto;

import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * PATCH /user/{id}. Email is deliberately not here: changing it needs its own verified flow.
 * Null fields are left unchanged.
 */
public record UpdateUserDTO(
        @Pattern(regexp = ".*\\S.*", message = "Full name is required")
        @Size(max = 80, message = "Full name must be at most 80 characters")
        String fullName
) {
}

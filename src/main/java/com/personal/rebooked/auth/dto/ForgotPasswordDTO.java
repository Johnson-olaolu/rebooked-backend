package com.personal.rebooked.auth.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotNull;

public record ForgotPasswordDTO(
        @NotNull @Email String email
) {
}

package com.personal.rebooked.auth.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

public record SendConfirmEmailDTO(
        @NotBlank @Email String email
) {
}

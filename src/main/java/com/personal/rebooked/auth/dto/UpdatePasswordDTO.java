package com.personal.rebooked.auth.dto;

import com.personal.rebooked.utils.Constants;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

/** POST /auth/change-password: a logged-in user changing their password from Settings. */
public record UpdatePasswordDTO(
        @NotBlank(message = "Enter your current password") String currentPassword,
        @NotBlank(message = Constants.PASSWORD_MESSAGE)
        @Pattern(regexp = Constants.PASSWORD_REGEX, message = Constants.PASSWORD_MESSAGE) String newPassword
) {
}

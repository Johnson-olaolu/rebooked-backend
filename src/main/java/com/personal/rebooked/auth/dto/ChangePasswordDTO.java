package com.personal.rebooked.auth.dto;

import com.personal.rebooked.utils.Constants;
import jakarta.validation.constraints.*;

public record ChangePasswordDTO (
        @NotBlank @Email String email,
        @NotBlank @Size(min = 6, max = 6) String token,
        @NotNull @NotBlank @Pattern(regexp = Constants.PASSWORD_REGEX, message = Constants.PASSWORD_MESSAGE) String password
){
}

package com.personal.rebooked.auth.dto;

import jakarta.validation.constraints.*;

public record ChangePasswordDTO (
        @NotBlank @Email String email,
        @NotBlank @Size(min = 6, max = 6) String token,
        @NotNull @NotBlank @Pattern(regexp = "^(?=.*[0-9])(?=.*[a-z])(?=.*[A-Z])(?=.*[_@#$%^&+=])(?=\\S+$).{8,}$") String password
){
}

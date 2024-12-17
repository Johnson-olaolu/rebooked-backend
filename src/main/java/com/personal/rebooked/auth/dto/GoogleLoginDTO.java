package com.personal.rebooked.auth.dto;

import jakarta.validation.constraints.NotBlank;

public record GoogleLoginDTO(
       @NotBlank String accessToken,
       @NotBlank String role
) {
    GoogleLoginDTO ( String accessToken){
        this(accessToken, "user");
    }
}

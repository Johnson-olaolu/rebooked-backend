package com.personal.rebooked.auth.dto;

import com.personal.rebooked.user.models.User;

import java.util.Date;

public record LoginResponseDTO(
        String token,
        long expiresIn,
        User user
) {
}

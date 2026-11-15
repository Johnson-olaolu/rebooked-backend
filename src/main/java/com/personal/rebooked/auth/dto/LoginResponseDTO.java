package com.personal.rebooked.auth.dto;

import com.personal.rebooked.user.models.User;

public record LoginResponseDTO(
        String token,
        long expiresIn,
        String refreshToken,
        User user
) {
}

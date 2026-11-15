package com.personal.rebooked.utils;

import com.personal.rebooked.auth.dto.LoginResponseDTO;
import com.personal.rebooked.user.models.User;

import java.util.Map;

/**
 * Swagger-only schemas that describe the { message, status, data } envelope.
 * Controllers still return ResponseEntity<Object>; these types are never instantiated.
 */
public final class ApiResponseDocs {
    private ApiResponseDocs() {}

    public record AuthResponse(String message, int status, LoginResponseDTO data) {}

    public record UserResponse(String message, int status, User data) {}

    public record EmptyResponse(String message, int status, Object data) {}

    public record ErrorResponse(int status, String message, Map<String, String> errors) {}
}

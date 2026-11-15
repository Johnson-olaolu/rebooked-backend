package com.personal.rebooked.exception;

import lombok.Getter;
import org.springframework.http.HttpStatus;

import java.util.Map;

/**
 * Throw from services when the client should receive a specific status and message,
 * optionally with per-field errors. Rendered by {@link GlobalExceptionHandler}.
 */
@Getter
public class ApiException extends RuntimeException {
    private final HttpStatus status;
    private final Map<String, String> errors;

    public ApiException(HttpStatus status, String message) {
        this(status, message, null);
    }

    public ApiException(HttpStatus status, String message, Map<String, String> errors) {
        super(message);
        this.status = status;
        this.errors = errors;
    }
}

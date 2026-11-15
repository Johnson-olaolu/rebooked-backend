package com.personal.rebooked.exception;

import com.personal.rebooked.utils.ResponseHandler;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.validation.BindException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Renders every error as { status, message, errors? }.
 * Runs before Spring Boot's ProblemDetailsExceptionHandler (enabled in application.properties).
 */
@Slf4j
@Order(Ordered.HIGHEST_PRECEDENCE)
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(ApiException.class)
    public ResponseEntity<Object> handleApi(ApiException ex) {
        return ResponseHandler.generateError(ex.getStatus(), ex.getMessage(), ex.getErrors());
    }

    // MethodArgumentNotValidException extends BindException, so this covers @RequestBody and @ModelAttribute
    @ExceptionHandler(BindException.class)
    public ResponseEntity<Object> handleValidation(BindException ex) {
        Map<String, String> errors = new LinkedHashMap<>();
        for (FieldError fieldError : ex.getBindingResult().getFieldErrors()) {
            errors.putIfAbsent(fieldError.getField(), fieldError.getDefaultMessage());
        }
        return ResponseHandler.generateError(HttpStatus.BAD_REQUEST, "Validation failed", errors);
    }

    @ExceptionHandler(org.springframework.web.multipart.MaxUploadSizeExceededException.class)
    public ResponseEntity<Object> handleTooLarge(org.springframework.web.multipart.MaxUploadSizeExceededException ex) {
        return ResponseHandler.generateError(HttpStatus.PAYLOAD_TOO_LARGE, "Photos must be 10 MB or smaller.",
                Map.of("file", "Photos must be 10 MB or smaller."));
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<Object> handleUnreadable(HttpMessageNotReadableException ex) {
        return ResponseHandler.generateError(HttpStatus.BAD_REQUEST, "Malformed request body", null);
    }

    @ExceptionHandler(ResponseStatusException.class)
    public ResponseEntity<Object> handleResponseStatus(ResponseStatusException ex) {
        HttpStatus status = HttpStatus.resolve(ex.getStatusCode().value());
        if (status == null) status = HttpStatus.INTERNAL_SERVER_ERROR;
        String message = ex.getReason() != null ? ex.getReason() : status.getReasonPhrase();
        return ResponseHandler.generateError(status, message, null);
    }

    @ExceptionHandler({BadCredentialsException.class, UsernameNotFoundException.class})
    public ResponseEntity<Object> handleBadCredentials(Exception ex) {
        return ResponseHandler.generateError(HttpStatus.UNAUTHORIZED, "Invalid email or password", null);
    }

    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<Object> handleAccessDenied(AccessDeniedException ex) {
        return ResponseHandler.generateError(HttpStatus.FORBIDDEN, "Forbidden", null);
    }

    @ExceptionHandler(NoResourceFoundException.class)
    public ResponseEntity<Object> handleNotFound(NoResourceFoundException ex) {
        return ResponseHandler.generateError(HttpStatus.NOT_FOUND, "Not found", null);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<Object> handleUnexpected(Exception ex) {
        log.error("Unhandled exception", ex);
        return ResponseHandler.generateError(HttpStatus.INTERNAL_SERVER_ERROR, "Something went wrong", null);
    }
}

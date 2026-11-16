package com.personal.rebooked.conversation;

import lombok.Getter;

import java.util.Map;

/** A chat command that failed; sent to the client as an "error" event, and never closes the session. */
@Getter
public class ChatException extends RuntimeException {
    public enum Code { VALIDATION, NOT_FOUND, FORBIDDEN, OWN_BOOK, BOOK_NOT_AVAILABLE, TOKEN_EXPIRED, SERVER }

    private final Code code;
    private final Map<String, String> errors;

    public ChatException(Code code, String message) {
        this(code, message, null);
    }

    public ChatException(Code code, String message, Map<String, String> errors) {
        super(message);
        this.code = code;
        this.errors = errors;
    }

    public static ChatException invalid(String field, String message) {
        return new ChatException(Code.VALIDATION, "Validation failed", Map.of(field, message));
    }

    public static ChatException notFound() {
        return new ChatException(Code.NOT_FOUND, "Not found");
    }
}

package com.personal.rebooked.book.dto;

import com.personal.rebooked.utils.Constants;
import jakarta.validation.constraints.NotNull;

public record UpdateBookStatusDTO(
        @NotNull(message = "Status is required") Constants.BookStatus status,
        /** Required for SOLD: one of GET /book/{id}/chat-buyers. */
        String buyerId
) {
}

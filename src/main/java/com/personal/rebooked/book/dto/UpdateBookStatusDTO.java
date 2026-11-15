package com.personal.rebooked.book.dto;

import com.personal.rebooked.utils.Constants;
import jakarta.validation.constraints.NotNull;

public record UpdateBookStatusDTO(
        @NotNull(message = "Status is required") Constants.BookStatus status
) {
}

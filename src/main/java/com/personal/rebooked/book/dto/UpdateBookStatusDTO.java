package com.personal.rebooked.book.dto;

import com.personal.rebooked.utils.Constants;

public record UpdateBookStatusDTO(
        Constants.BookStatus status
) {
}

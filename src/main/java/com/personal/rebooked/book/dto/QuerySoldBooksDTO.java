package com.personal.rebooked.book.dto;

import com.personal.rebooked.utils.Constants;
import jakarta.validation.constraints.NotNull;

public record QuerySoldBooksDTO(
        String userId,
        @NotNull Constants.TimeQuery timeQuery
) {
    public QuerySoldBooksDTO {
        if (timeQuery == null ) {
            timeQuery = Constants.TimeQuery.LAST_WEEK; // Default time range
        }
    }
}

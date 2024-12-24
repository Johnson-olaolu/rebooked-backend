package com.personal.rebooked.book.dto;

import com.personal.rebooked.utils.Constants;
import jakarta.validation.constraints.NotBlank;

public record QuerySoldBooksDTO(
        String userId,
        @NotBlank Constants.TimeQuery timeQuery
) {
    public QuerySoldBooksDTO {
        if (timeQuery == null ) {
            timeQuery = Constants.TimeQuery.LAST_WEEK; // Default time range
        }
    }
}

package com.personal.rebooked.book.dto;

import java.util.Collections;
import java.util.List;

public record QueryBookDTO
        (
                String userId,
                List<String> categoryIds,
                String search,
                Integer page,
                Integer pageSize
        ) {
        // Canonical constructor with default values
        public QueryBookDTO {
                if(categoryIds == null || categoryIds.isEmpty()) {
                        categoryIds = Collections.emptyList();
                }
                if (page < 0) {
                        page = 0; // Default page number
                }
                if (pageSize <= 0) {
                        pageSize = 10; // Default page size
                }
        }
}

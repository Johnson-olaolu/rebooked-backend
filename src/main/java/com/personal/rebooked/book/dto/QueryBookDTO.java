package com.personal.rebooked.book.dto;

import java.util.Collections;
import java.util.List;

public record QueryBookDTO(
        String userId,
        List<String> categoryIds,
        String search,
        Integer page,
        Integer pageSize) {

    public QueryBookDTO {
        if (categoryIds == null) categoryIds = Collections.emptyList();
        if (page == null || page < 0) page = 0;
        if (pageSize == null || pageSize <= 0) pageSize = 20;
        if (pageSize > 50) pageSize = 50;
    }
}

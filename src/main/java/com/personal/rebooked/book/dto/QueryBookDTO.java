package com.personal.rebooked.book.dto;

import java.util.Collections;
import java.util.List;

/**
 * GET /book/query. Every param is optional; list params are comma-separated.
 * Enum-like values (conditions, sort, status) are strings so bad values become field errors, not 500s.
 */
public record QueryBookDTO(
        String userId,
        List<String> categoryIds,
        String search,
        /** Only books by this author (a merged id resolves to its canonical author) */
        String authorId,
        List<String> states,
        List<String> conditions,
        Double minPrice,
        Double maxPrice,
        Boolean free,
        /** RELEVANCE (default when search is set), NEWEST (default otherwise), PRICE_ASC or PRICE_DESC */
        String sort,
        /** ACTIVE (default) or SOLD; SOLD requires userId */
        String status,
        Integer page,
        Integer pageSize) {

    public QueryBookDTO {
        if (categoryIds == null) categoryIds = Collections.emptyList();
        if (states == null) states = Collections.emptyList();
        if (conditions == null) conditions = Collections.emptyList();
        if (page == null || page < 0) page = 0;
        if (pageSize == null || pageSize <= 0) pageSize = 20;
        if (pageSize > 50) pageSize = 50;
    }
}

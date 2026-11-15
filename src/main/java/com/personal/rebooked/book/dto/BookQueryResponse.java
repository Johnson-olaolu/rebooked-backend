package com.personal.rebooked.book.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.personal.rebooked.book.models.Book;

import java.util.List;
import java.util.Map;

/** GET /book/query. facets and addedToday are only present for ACTIVE (shop) queries. */
public record BookQueryResponse(
        List<Book> content, int page, int pageSize, long totalElements, int totalPages,
        @JsonInclude(JsonInclude.Include.NON_NULL) Facets facets,
        @JsonInclude(JsonInclude.Include.NON_NULL) Long addedToday) {

    /** Each group is counted with every other active filter applied, but not its own. */
    public record Facets(Map<String, Long> categories, Map<String, Long> states,
                         Map<String, Long> conditions, long free) {
    }
}

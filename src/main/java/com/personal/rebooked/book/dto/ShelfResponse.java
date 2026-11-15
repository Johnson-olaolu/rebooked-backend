package com.personal.rebooked.book.dto;

import com.personal.rebooked.book.models.Book;

import java.util.List;
import java.util.Map;

/** GET /book/me: one page of the caller's books plus counts per status across all of them. */
public record ShelfResponse(List<Book> content, int page, int pageSize, long totalElements, int totalPages,
                            Map<String, Long> counts) {
}

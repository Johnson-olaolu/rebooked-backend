package com.personal.rebooked.book.dto;

import java.util.List;

public record PageResponse<T>(List<T> content, int page, int pageSize, long totalElements, int totalPages) {

    public static <T> PageResponse<T> of(List<T> content, int page, int pageSize, long total) {
        return new PageResponse<>(content, page, pageSize, total, (int) Math.ceil((double) total / pageSize));
    }
}

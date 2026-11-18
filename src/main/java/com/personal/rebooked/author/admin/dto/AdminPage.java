package com.personal.rebooked.author.admin.dto;

import java.util.List;

/** The usual page shape for the admin lists. */
public record AdminPage<T>(List<T> content, int page, int pageSize, long totalElements, int totalPages) {

    public static <T> AdminPage<T> of(List<T> content, int page, int pageSize, long total) {
        return new AdminPage<>(content, page, pageSize, total, (int) ((total + pageSize - 1) / pageSize));
    }
}

package com.personal.rebooked.book.dto;


import jakarta.validation.constraints.NotNull;

import java.util.List;

public record CreateBookDto(
        @NotNull
        String title,

        String author,

        @NotNull
        Double price,

        String description,

        String coverImageId,

        List<String> imageIds,

        List<String> categoryIds
) {
}

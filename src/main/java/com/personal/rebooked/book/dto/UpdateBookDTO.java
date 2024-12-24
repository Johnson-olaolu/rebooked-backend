package com.personal.rebooked.book.dto;

import jakarta.validation.constraints.NotBlank;

import java.util.List;

public record UpdateBookDTO(
        String title,
        String author,
        Double price,
        String coverImageId,
        List<String> imageIds,
        List<String> categoryIds
) {
}

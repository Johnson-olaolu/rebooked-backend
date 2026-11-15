package com.personal.rebooked.book.dto;

import com.personal.rebooked.utils.Constants;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.util.List;

/** PATCH /book/{id}. null = keep; "" clears author, isbn and description. */
public record UpdateBookDTO(
        @Pattern(regexp = ".*\\S.*", message = "Title is required")
        @Size(max = 200, message = "Title must be at most 200 characters")
        String title,

        @Size(max = 120, message = "Author must be at most 120 characters")
        String author,

        Double price,

        Boolean isFree,

        Constants.BookCondition condition,

        String isbn,

        @Size(max = 600, message = "Description must be at most 600 characters")
        String description,

        String coverImageId,

        List<String> imageIds,

        List<String> categoryIds,

        List<String> tagNames
) {
}

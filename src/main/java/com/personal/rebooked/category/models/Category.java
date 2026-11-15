package com.personal.rebooked.category.models;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.annotation.Transient;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.LocalDateTime;
import java.util.Date;

@Data
@Document
public class Category {
    @Id
    private String id;

    @NotBlank
    @Indexed(unique = true)
    private String name;

    private Boolean isSeeded;

    private String description;

    private String imageUrl;

    /** Number of books in this category. Computed for /category/top, never stored. */
    @Transient
    private Integer booksCount;

    @CreatedDate
    private Date createdAt;

    @LastModifiedDate
    private Date updatedAt;
}

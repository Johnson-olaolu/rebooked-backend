package com.personal.rebooked.book.models;

import com.personal.rebooked.category.models.Category;
import com.personal.rebooked.file.models.File;
import com.personal.rebooked.user.models.User;
import com.personal.rebooked.utils.Constants;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.mongodb.core.mapping.DBRef;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.LocalDateTime;
import java.util.Date;
import java.util.List;

@Data
@Document
public class Book {
    @Id
    private String id;

    @DBRef
    User user;

    @NotBlank
    private String title;

    private String author;

    @NotBlank
    private Double price;

    private String description;

    @DBRef
    private File coverImage;

    @DBRef
    private List<File> images;

//    private Integer stock = 1;
    @DBRef
    private List<Category> categories;

    private Constants.BookStatus status = Constants.BookStatus.ACTIVE;

    private Date soldDate;

    @CreatedDate
    private LocalDateTime createdAt;

    @LastModifiedDate
    private LocalDateTime updatedAt;
}

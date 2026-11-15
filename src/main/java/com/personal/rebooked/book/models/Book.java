package com.personal.rebooked.book.models;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.personal.rebooked.category.models.Category;
import com.personal.rebooked.file.models.File;
import com.personal.rebooked.user.dto.PublicUserDTO;
import com.personal.rebooked.user.models.User;
import com.personal.rebooked.utils.Constants;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.mongodb.core.mapping.DBRef;
import org.springframework.data.mongodb.core.mapping.Document;

import java.util.Date;
import java.util.List;

@Data
@Document
public class Book {
    @Id
    private String id;

    /** The seller. Never serialised whole: responses get the public summary in {@link #getSeller()}. */
    @JsonIgnore
    @DBRef
    User user;

    @NotBlank
    private String title;

    private String author;

    /** In naira. 0 when the book is free. */
    private Double price;

    @JsonProperty("isFree")
    private boolean isFree = false;

    private Constants.BookCondition condition;

    /** Normalised: digits only (ISBN-10 may end in X). */
    private String isbn;

    private String description;

    @DBRef
    private File coverImage;

    @DBRef
    private List<File> images;

    private Integer viewCount = 0;

    @DBRef
    private List<Category> categories;

    @DBRef
    private List<Tag> tags;

    private Constants.BookStatus status = Constants.BookStatus.ACTIVE;

    private Date soldDate;

    @CreatedDate
    private Date createdAt;

    @LastModifiedDate
    private Date updatedAt;

    @JsonProperty("seller")
    public PublicUserDTO getSeller() {
        return PublicUserDTO.from(user);
    }
}

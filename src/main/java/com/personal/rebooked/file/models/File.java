package com.personal.rebooked.file.models;

import com.personal.rebooked.user.models.User;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.mongodb.core.mapping.DBRef;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.LocalDateTime;

@Data
@Document
public class File {
    @Id
    private String id;

    @DBRef
    private User user;

    @NotBlank
    private String name;

    @NotBlank
    private String url;

    @NotBlank
    private String mimetype;

    @NotBlank
    private String size;

    @NotBlank
    private String cloudinaryId;

    @CreatedDate
    private LocalDateTime createdAt;

    @LastModifiedDate
    private LocalDateTime updatedAt;
}

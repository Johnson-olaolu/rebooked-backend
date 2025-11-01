package com.personal.rebooked.file.models;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.personal.rebooked.user.models.User;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;
import lombok.ToString;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.mongodb.core.mapping.DBRef;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.LocalDateTime;
import java.util.Date;

@JsonIgnoreProperties(ignoreUnknown = true)
@Data
@Document
@ToString(exclude = {"user"})
public class File {
    @Id
    private String id;

    @DBRef(lazy = true)
    @JsonIgnore
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
    private Date createdAt;

    @LastModifiedDate
    private Date updatedAt;
}

package com.personal.rebooked.user.models;

import com.personal.rebooked.file.models.File;
import lombok.Data;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.mongodb.core.mapping.DBRef;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.LocalDateTime;

@Data
@Document("profile")
public class Profile {
    @Id
    private String id;

    private String userName;

    private String bio;

    private String contactEmail;

    private String contactPhone;

    @DBRef
    private File profilePicture;

    private String profilePictureUrl;

    @DBRef
    private Address address;

    @CreatedDate
    private LocalDateTime createdAt;

    @LastModifiedDate
    private LocalDateTime updatedAt;
}

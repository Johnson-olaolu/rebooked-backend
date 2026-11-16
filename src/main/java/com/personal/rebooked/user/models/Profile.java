package com.personal.rebooked.user.models;

import com.personal.rebooked.file.models.File;
import lombok.Data;
import org.springframework.data.annotation.ReadOnlyProperty;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.mongodb.core.mapping.DBRef;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.LocalDateTime;
import java.util.Date;

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

    private Address address;

    /** Set by the server when userName and address.city are filled in. Never accepted from clients. */
    private boolean onboardingCompleted = false;

    /**
     * Read-only for the mapping: saving a Profile never writes it, so a profile edit can't overwrite
     * a rating saved at the same moment. RatingService updates it atomically.
     */
    @ReadOnlyProperty
    private SellerRatingSummary sellerRating;

    @CreatedDate
    private Date createdAt;

    @LastModifiedDate
    private Date updatedAt;
}

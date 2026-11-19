package com.personal.rebooked.homepage.dto;

import com.personal.rebooked.file.models.File;

import java.util.Date;

/** reviews[]: never the reviewer's email, full name or id. */
public record ReviewCard(int rating, String comment, Date createdAt, Reviewer reviewer, ReviewBook book) {

    public record Reviewer(String userName, String profilePictureUrl) {
    }

    public record ReviewBook(String id, String title, File coverImage) {
    }
}

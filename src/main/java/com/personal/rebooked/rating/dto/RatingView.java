package com.personal.rebooked.rating.dto;

import java.util.Date;

/** GET and PUT /book/{id}/rating: everything the rating form needs. */
public record RatingView(boolean canRate, Mine rating, Seller seller, Book book, String conversationId) {
    public record Mine(int stars, String comment, Date updatedAt) {
    }

    public record Seller(String id, String userName, String profilePictureUrl) {
    }

    public record Book(String id, String title, String slug, String coverUrl) {
    }
}

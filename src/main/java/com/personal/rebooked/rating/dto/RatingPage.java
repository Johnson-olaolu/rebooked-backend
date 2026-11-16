package com.personal.rebooked.rating.dto;

import java.util.Date;
import java.util.List;

/** GET /user/{id}/ratings: a seller's reviews, newest first. */
public record RatingPage(List<Review> content, int page, int pageSize, long totalElements, int totalPages) {
    public record Review(String id, int stars, String comment, Date createdAt, Buyer buyer, Book book) {
    }

    /** No id: reviews don't link to the buyer's account. */
    public record Buyer(String userName, String profilePictureUrl) {
    }

    /** null when the book has since been deleted. */
    public record Book(String id, String title, String slug) {
    }
}

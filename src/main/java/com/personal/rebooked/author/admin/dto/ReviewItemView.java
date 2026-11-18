package com.personal.rebooked.author.admin.dto;

import com.personal.rebooked.author.models.Author;
import com.personal.rebooked.author.models.AuthorReviewItem;

import java.util.Date;
import java.util.List;

/** GET /admin/author/review rows. otherAuthor is null for PROPOSAL items. */
public record ReviewItemView(String id, AuthorReviewItem.Type type, AuthorReviewItem.ItemStatus status,
                             AuthorReviewItem.Resolution resolution, String reason, Date createdAt, Date resolvedAt,
                             ReviewAuthor author, ReviewAuthor otherAuthor) {

    /** AuthorSummary plus origin, status, soldCount and external ids. */
    public record ReviewAuthor(String id, String name, String slug, boolean verified, Integer birthYear,
                               Integer deathYear, String description, long availableCount, Author.Origin origin,
                               Author.Status status, long soldCount, List<ExternalIdView> externalIds) {

        public static ReviewAuthor of(Author a, List<ExternalIdView> externalIds) {
            if (a == null) return null;
            return new ReviewAuthor(a.getId(), a.getName(), a.getSlug(), a.verified(), a.getBirthYear(),
                    a.getDeathYear(), a.getDescription(), a.getAvailableCount(), a.getOrigin(), a.getStatus(),
                    a.getSoldCount(), externalIds);
        }
    }
}

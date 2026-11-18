package com.personal.rebooked.author.admin.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.personal.rebooked.author.models.Author;
import com.personal.rebooked.author.models.AuthorReviewItem;
import com.personal.rebooked.user.dto.PublicUserDTO;

import java.util.Date;
import java.util.List;
import java.util.Map;

/**
 * GET /admin/author/{id}: every field, unfiltered by the licensing gate, plus where each came from. bookCount (books in
 * any status) is only set in the merge preview.
 */
public record AdminAuthorDetail(String id, String name, String slug, String normalizedName, boolean verified,
                                Author.Status status, Author.Origin origin, String mergedIntoAuthorId,
                                String biography, String description, Integer birthYear, Integer deathYear,
                                Author.Photo photo, Author.Links links, List<String> countries, int workCount,
                                long availableCount, long soldCount, Map<String, Author.Source> fieldSources,
                                List<Alias> aliases, List<ExternalIdView> externalIds, PublicUserDTO createdBy,
                                List<ReviewItemBrief> openReviewItems, Date createdAt, Date updatedAt,
                                @JsonInclude(JsonInclude.Include.NON_NULL) Long bookCount) {

    public record Alias(String name, Author.Source source) {}

    public record ReviewItemBrief(String id, AuthorReviewItem.Type type, String authorId, String otherAuthorId,
                                  String reason, Date createdAt) {

        public static ReviewItemBrief of(AuthorReviewItem item) {
            return new ReviewItemBrief(item.getId(), item.getType(), item.getAuthorId(), item.getOtherAuthorId(),
                    item.getReason(), item.getCreatedAt());
        }
    }

    public AdminAuthorDetail withBookCount(long count) {
        return new AdminAuthorDetail(id, name, slug, normalizedName, verified, status, origin, mergedIntoAuthorId,
                biography, description, birthYear, deathYear, photo, links, countries, workCount, availableCount,
                soldCount, fieldSources, aliases, externalIds, createdBy, openReviewItems, createdAt, updatedAt, count);
    }
}

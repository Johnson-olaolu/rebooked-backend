package com.personal.rebooked.author.admin.dto;

import com.personal.rebooked.author.models.Author;
import com.personal.rebooked.author.models.AuthorExternalIdentifier.IdSource;

import java.util.List;

/** GET /admin/author rows: AuthorSummary plus origin, status, soldCount, openReviewItems and the external id sources. */
public record AdminAuthorRow(String id, String name, String slug, boolean verified, Integer birthYear, Integer deathYear,
                             String description, long availableCount, String matchedAlias, Author.Origin origin,
                             Author.Status status, long soldCount, long openReviewItems, List<IdSource> sources) {

    public static AdminAuthorRow of(Author a, long openReviewItems, List<IdSource> sources) {
        return new AdminAuthorRow(a.getId(), a.getName(), a.getSlug(), a.verified(), a.getBirthYear(), a.getDeathYear(),
                a.getDescription(), a.getAvailableCount(), null, a.getOrigin(), a.getStatus(), a.getSoldCount(),
                openReviewItems, sources);
    }
}

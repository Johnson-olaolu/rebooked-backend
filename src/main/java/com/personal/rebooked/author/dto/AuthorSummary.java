package com.personal.rebooked.author.dto;

import com.personal.rebooked.author.models.Author;

/**
 * An author in search results, proposals and top authors (§23c). matchedAlias is the alias that matched the query
 * when the match came from an alias rather than the name.
 */
public record AuthorSummary(String id, String name, String slug, boolean verified, Integer birthYear, Integer deathYear,
                            String description, long availableCount, String matchedAlias) {

    public static AuthorSummary of(Author author) {
        return of(author, null);
    }

    public static AuthorSummary of(Author author, String matchedAlias) {
        if (author == null) return null;
        return new AuthorSummary(author.getId(), author.getName(), author.getSlug(), author.verified(),
                author.getBirthYear(), author.getDeathYear(), author.getDescription(), author.getAvailableCount(),
                matchedAlias);
    }
}

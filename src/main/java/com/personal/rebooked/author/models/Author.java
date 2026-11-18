package com.personal.rebooked.author.models;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.index.CompoundIndexes;
import org.springframework.data.mongodb.core.index.IndexDirection;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.util.*;

/**
 * An author in the catalogue (§23a). Books point at authors by id and keep a display cache of name and slug.
 * The partial index for search (authors with books) is created in AuthorService.ensureIndexes().
 */
@Data
@Document("authors")
@CompoundIndexes({
        @CompoundIndex(name = "status_updated", def = "{'status': 1, 'updatedAt': -1}")
})
public class Author {
    @Id
    private String id;

    /** Display name, as written. */
    private String name;

    /** AuthorNames.normalize(name). Not unique: two people can share a name. */
    @Indexed(name = "normalized_name")
    private String normalizedName;

    /** Set once at creation; a rename never changes it. */
    @Indexed(name = "slug_unique", unique = true)
    private String slug;

    /** AuthorNames.searchKeys(name, aliases). */
    @Indexed(name = "search_keys")
    private List<String> searchKeys = new ArrayList<>();

    private List<Alias> aliases = new ArrayList<>();

    /** Plain text. */
    private String biography;

    /** Short disambiguation line, e.g. "Nigerian novelist". */
    private String description;

    private Integer birthYear;
    private Integer deathYear;

    private Photo photo;

    private Links links;

    /** ISO 3166-1 alpha-2 codes, from Wikidata. */
    private List<String> countries;

    /** Works in Open Library, saturating at 127. Used for ranking. */
    private int workCount;

    private Origin origin;

    private Status status;

    private String mergedIntoAuthorId;

    /** Which source last set each field (name, biography, description, birthYear, deathYear, photo, countries). */
    private Map<String, Source> fieldSources = new HashMap<>();

    /** The seller who proposed it (USER_PROPOSED). */
    private String createdBy;

    /** ACTIVE books with this author. Stored on write by AuthorStats, never computed on read. */
    @Indexed(name = "available_count", direction = IndexDirection.DESCENDING)
    private long availableCount;

    /** SOLD books with this author. */
    private long soldCount;

    private Date createdAt;

    /** The admin list sorts on it, with or without a status filter. */
    @Indexed(name = "updated_at", direction = IndexDirection.DESCENDING)
    private Date updatedAt;

    public boolean verified() {
        return status == Status.VERIFIED;
    }

    public Source fieldSource(String field) {
        return fieldSources == null ? null : fieldSources.get(field);
    }

    /** Never null, even for a document stored with aliases: null. */
    public List<Alias> aliasList() {
        return aliases == null ? List.of() : aliases;
    }

    public List<String> aliasNames() {
        return aliasList().stream().map(Alias::getName).filter(Objects::nonNull).toList();
    }

    public enum Origin { IMPORTED, USER_PROPOSED, ADMIN_CREATED, MIGRATED }

    public enum Status { UNVERIFIED, VERIFIED, NEEDS_REVIEW, MERGED }

    /** Where a field or alias came from. USER is a seller's proposal. */
    public enum Source {
        OPEN_LIBRARY, WIKIDATA, ADMIN, MIGRATION, USER;

        public boolean external() {
            return this == OPEN_LIBRARY || this == WIKIDATA;
        }
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Alias {
        private String name;
        private String normalizedName;
        private Source source;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Photo {
        private String url;
        private Source source;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Links {
        private String openLibrary;
        private String wikidata;
        private String wikipedia;
    }
}

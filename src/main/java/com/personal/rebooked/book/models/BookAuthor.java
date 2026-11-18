package com.personal.rebooked.book.models;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * One author on a book (§23b). authorId is the source of truth; name and slug are a display cache that
 * AuthorService refreshes on renames and merges. In JSON: { id, name, slug }. Books store their authors in
 * position order, so the array is already sorted.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class BookAuthor {
    /** Stored as a string. Not called "id": Spring Data would treat that as the document id. */
    @JsonProperty("id")
    private String authorId;

    @JsonIgnore
    private int position;

    private String name;

    private String slug;
}

package com.personal.rebooked.homepage.dto;

import com.fasterxml.jackson.annotation.JsonUnwrapped;
import com.personal.rebooked.author.dto.AuthorDetail;
import com.personal.rebooked.book.models.Book;
import com.personal.rebooked.category.models.Category;
import com.personal.rebooked.homepage.models.FeatureSource;

import java.util.List;

/** featured: this week's category with its books, and an author with some of theirs. Either may be null. */
public record FeaturedView(Category category, List<Book> books, Author author, FeatureSource source) {

    /** The public author detail plus up to 4 of their ACTIVE books. */
    public record Author(@JsonUnwrapped AuthorDetail detail, List<Book> books) {
    }
}

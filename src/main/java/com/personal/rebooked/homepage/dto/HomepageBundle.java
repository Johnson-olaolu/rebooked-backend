package com.personal.rebooked.homepage.dto;

import com.personal.rebooked.book.models.Book;
import com.personal.rebooked.category.models.Category;
import com.personal.rebooked.user.dto.PublicUserDTO;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

/** GET /homepage (§24b). A section that failed is null (objects) or [] (lists). */
public record HomepageBundle(Instant generatedAt, LocalDate weekOf, List<Book> hero, HomepageStats stats,
                             List<Category> categories, FeaturedView featured, List<AiPick> aiPicks,
                             List<Book> trending, List<Book> newArrivals, List<TopAuthorView> topAuthors,
                             List<ReviewCard> reviews, List<PublicUserDTO> sellers) {
}

package com.personal.rebooked.homepage.sections;

import com.fasterxml.jackson.core.type.TypeReference;
import com.personal.rebooked.category.CategoryService;
import com.personal.rebooked.category.models.Category;
import com.personal.rebooked.homepage.HomepageCache;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.List;

/** §24c categories: the 8 seeded categories with the most ACTIVE books, with booksCount. Built on a miss. */
@Component
@RequiredArgsConstructor
public class CategoriesSection {
    public static final String KEY = "homepage:categories";
    static final Duration TTL = Duration.ofHours(1);
    static final int LIMIT = 8;

    private final HomepageCache cache;
    private final CategoryService categoryService;

    public List<Category> read() {
        return cache.get(KEY, new TypeReference<List<Category>>() {}).orElseGet(this::build);
    }

    public List<Category> build() {
        List<Category> categories = categoryService.fetchTopCategories(LIMIT);
        cache.put(KEY, categories, TTL);
        return categories;
    }
}

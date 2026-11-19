package com.personal.rebooked.homepage.models;

import lombok.Data;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * One week's featured set (§24d). The id fields hold the resolved choice for the week, whoever made it;
 * {@code adminFields} lists the ones an admin chose, so the rest can be re-picked automatically and an override
 * can be dropped. Kept as history so picks don't repeat.
 */
@Data
@Document("homepage_features")
public class HomepageFeature {
    public static final String CATEGORY = "categoryId";
    public static final String BOOKS = "bookIds";
    public static final String AUTHOR = "authorId";
    public static final String HERO = "heroBookIds";

    @Id
    private String id;

    /** Monday, Africa/Lagos. */
    @Indexed(unique = true)
    private LocalDate weekStart;

    private String categoryId;
    /** ≤ 6, ordered. */
    private List<String> bookIds = new ArrayList<>();
    private String authorId;
    /** ≤ 3, ordered. */
    private List<String> heroBookIds = new ArrayList<>();

    /** ADMIN when any field was chosen by an admin. */
    private FeatureSource source = FeatureSource.AUTO;
    private Set<String> adminFields = new LinkedHashSet<>();
    /** The admin's user id when ADMIN. */
    private String createdBy;
    private String note;

    private Instant createdAt;
    private Instant updatedAt;
}

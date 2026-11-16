package com.personal.rebooked.book.views;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import java.util.Date;
import java.util.List;

/** The Mongo copy of a user's recently viewed books (newest first, at most 20). Redis holds the live list. */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Document("recent_views")
public class RecentViews {
    /** The user's id. */
    @Id
    private String id;
    private List<Item> items;
    private Date updatedAt;

    public record Item(String bookId, long viewedAt) {
    }
}

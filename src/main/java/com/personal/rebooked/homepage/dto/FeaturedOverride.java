package com.personal.rebooked.homepage.dto;

import java.util.List;

/** PUT /admin/homepage/featured. Every field is optional; omitted ones keep the AUTO choice. */
public record FeaturedOverride(String categoryId, List<String> bookIds, String authorId, List<String> heroBookIds,
                               String note) {
}

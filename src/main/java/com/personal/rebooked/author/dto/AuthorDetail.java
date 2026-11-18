package com.personal.rebooked.author.dto;

import java.util.List;

/**
 * GET /author/{id} and /author/slug/{slug}. A merged author answers with the canonical one, so slug may differ from
 * the one requested. biography and photoUrl from an external source are null until the licensing review (§23c).
 */
public record AuthorDetail(String id, String name, String slug, boolean verified, Integer birthYear, Integer deathYear,
                           String description, long availableCount, long soldCount, String biography, String photoUrl,
                           List<String> aliases, Links links, List<String> attribution) {

    public record Links(String openLibrary, String wikidata, String wikipedia) {}
}

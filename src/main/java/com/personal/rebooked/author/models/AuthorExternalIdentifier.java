package com.personal.rebooked.author.models;

import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.index.CompoundIndexes;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.util.Date;

/**
 * An id for an author in another catalogue. A separate collection, because a unique multikey index on an array
 * wouldn't enforce uniqueness per (source, sourceId) pair.
 */
@Data
@NoArgsConstructor
@Document("author_external_identifiers")
@CompoundIndexes({
        @CompoundIndex(name = "source_id_unique", def = "{'source': 1, 'sourceId': 1}", unique = true)
})
public class AuthorExternalIdentifier {
    @Id
    private String id;

    @Indexed(name = "author_id")
    private String authorId;

    private IdSource source;

    /** OL…A, Q…, a VIAF number or a 16-character ISNI. */
    private String sourceId;

    /** Who linked it: OPEN_LIBRARY (the import) or ADMIN. Wikidata enrichment only trusts OPEN_LIBRARY links. */
    private Author.Source addedBy;

    private Date createdAt;

    public AuthorExternalIdentifier(String authorId, IdSource source, String sourceId, Author.Source addedBy) {
        this.authorId = authorId;
        this.source = source;
        this.sourceId = sourceId;
        this.addedBy = addedBy;
        this.createdAt = new Date();
    }

    public enum IdSource {
        OPEN_LIBRARY, WIKIDATA, VIAF, ISNI;

        /** Canonical form, or null when the id isn't valid for this source. */
        public String normalize(String raw) {
            if (raw == null) return null;
            String s = raw.trim().replaceAll("\\s+", "").toUpperCase(java.util.Locale.ROOT);
            return switch (this) {
                case OPEN_LIBRARY -> s.matches("OL\\d+A") ? s : null;
                case WIKIDATA -> s.matches("Q\\d+") ? s : null;
                case VIAF -> s.matches("\\d{1,22}") ? s : null;
                case ISNI -> s.matches("\\d{15}[\\dX]") ? s : null;
            };
        }

        public String url(String sourceId) {
            return switch (this) {
                case OPEN_LIBRARY -> "https://openlibrary.org/authors/" + sourceId;
                case WIKIDATA -> "https://www.wikidata.org/wiki/" + sourceId;
                case VIAF -> "https://viaf.org/viaf/" + sourceId;
                case ISNI -> "https://isni.org/isni/" + sourceId;
            };
        }
    }
}

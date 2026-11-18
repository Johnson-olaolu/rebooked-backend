package com.personal.rebooked.author.importer;

import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.index.CompoundIndexes;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.util.Date;

/** A record an import couldn't take. Kept 90 days. */
@Data
@NoArgsConstructor
@Document("author_import_errors")
@CompoundIndexes({
        @CompoundIndex(name = "job_line", def = "{'jobId': 1, 'line': 1}")
})
public class AuthorImportError {
    @Id
    private String id;

    private String jobId;

    /** Line in the dump, or the position in the Wikidata pass. */
    private long line;

    private String sourceId;

    private String message;

    @Indexed(name = "ttl_90_days", expireAfter = "90d")
    private Date createdAt;

    public AuthorImportError(String jobId, long line, String sourceId, String message) {
        this.jobId = jobId;
        this.line = line;
        this.sourceId = sourceId;
        this.message = message;
        this.createdAt = new Date();
    }
}

package com.personal.rebooked.author.importer;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.index.CompoundIndexes;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.core.mapping.Field;

import java.util.Date;

/** One run of the Open Library import or the Wikidata enrichment (§23g, §23h). Shown on the admin Imports page. */
@Data
@Document("author_import_jobs")
@CompoundIndexes({
        @CompoundIndex(name = "source_version_status", def = "{'source': 1, 'datasetVersion': 1, 'status': 1}"),
        @CompoundIndex(name = "created", def = "{'createdAt': -1}")
})
public class AuthorImportJob {
    @Id
    private String id;

    private ImportSource source;

    /** The Open Library dump date, e.g. 2026-10-31. */
    private String datasetVersion;

    private String sourceFile;

    private Status status;

    private long recordsRead;
    private long recordsCreated;
    private long recordsUpdated;
    private long recordsSkipped;
    private long recordsNeedingReview;
    private long recordsFailed;

    /** Where a resumed run picks up: the pass (WORKS, AUTHORS, REDIRECTS, WIKIDATA) and the last line done. */
    private Checkpoint checkpoint;

    private String errorSummary;

    /** e.g. "Dump 2026-10-31 was already imported", "Resumed from job …". */
    private String note;

    /** null for the scheduled run. */
    private String actorId;

    /** Wikidata only: authors with Nigeria, and with any African country, among their countries. */
    private Coverage coverage;

    private Date createdAt;
    private Date startedAt;
    private Date completedAt;

    public enum ImportSource { OPEN_LIBRARY, WIKIDATA }

    public enum Status { PENDING, RUNNING, COMPLETED, FAILED }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Checkpoint {
        private String pass;
        private long lineNumber;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Coverage {
        @JsonProperty("NG")
        @Field("NG")
        private long ng;
        private long africa;
    }
}

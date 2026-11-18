package com.personal.rebooked.author.importer;

import lombok.extern.slf4j.Slf4j;

/**
 * Counters, checkpoints and error rows for one running job. Saves the job (and renews the lock) at each checkpoint,
 * and at most every 30 seconds in between.
 */
@Slf4j
public class ImportProgress {
    private static final int MAX_ERROR_ROWS = 10_000;
    private static final long SAVE_EVERY_MS = 30_000;

    private final AuthorImportJob job;
    private final AuthorImportJobRepository jobs;
    private final AuthorImportErrorRepository errors;
    private final ImportLock lock;
    private long errorRows;
    private long lastSave = System.currentTimeMillis();

    public ImportProgress(AuthorImportJob job, AuthorImportJobRepository jobs, AuthorImportErrorRepository errors, ImportLock lock) {
        this.job = job;
        this.jobs = jobs;
        this.errors = errors;
        this.lock = lock;
    }

    public AuthorImportJob job() {
        return job;
    }

    public void read() { job.setRecordsRead(job.getRecordsRead() + 1); }

    public void created() { job.setRecordsCreated(job.getRecordsCreated() + 1); }

    public void updated() { job.setRecordsUpdated(job.getRecordsUpdated() + 1); }

    public void skipped() { job.setRecordsSkipped(job.getRecordsSkipped() + 1); }

    public void needsReview() { job.setRecordsNeedingReview(job.getRecordsNeedingReview() + 1); }

    /** Counts a failure and keeps the first 10,000 as error rows. */
    public void failed(long line, String sourceId, String message) {
        job.setRecordsFailed(job.getRecordsFailed() + 1);
        note(line, sourceId, message);
    }

    /** An error row that isn't a failed record (e.g. an id left unlinked). */
    public void note(long line, String sourceId, String message) {
        if (errorRows++ >= MAX_ERROR_ROWS) return;
        String text = message == null ? "Unknown error" : message.length() > 500 ? message.substring(0, 500) : message;
        errors.insert(new AuthorImportError(job.getId(), line, sourceId, text));
    }

    public void checkpoint(String pass, long line) {
        job.setCheckpoint(new AuthorImportJob.Checkpoint(pass, line));
        save();
    }

    /** For long stretches without checkpoints (downloads, the works pass). */
    public void heartbeat() {
        if (System.currentTimeMillis() - lastSave >= SAVE_EVERY_MS) save();
    }

    public void save() {
        jobs.save(job);
        if (!lock.renew()) log.warn("Author import {} no longer holds the import lock", job.getId());
        lastSave = System.currentTimeMillis();
    }
}

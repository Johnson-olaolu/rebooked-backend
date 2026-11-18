package com.personal.rebooked.author.importer;

import com.personal.rebooked.author.admin.dto.AdminPage;
import com.personal.rebooked.author.importer.AuthorImportJob.ImportSource;
import com.personal.rebooked.author.importer.AuthorImportJob.Status;
import com.personal.rebooked.author.migration.AuthorMigration;
import com.personal.rebooked.exception.ApiException;
import lombok.extern.slf4j.Slf4j;
import org.bson.types.ObjectId;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.task.TaskExecutor;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.http.HttpStatus;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.nio.file.Path;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import static org.springframework.data.mongodb.core.query.Criteria.where;
import static org.springframework.data.mongodb.core.query.Query.query;

/**
 * Runs the author imports (§23g, §23h) on a single-thread executor, one at a time across instances (ImportLock).
 * Every job is recorded in author_import_jobs, with a checkpoint so an interrupted run can pick up where it stopped.
 */
@Slf4j
@Service
public class AuthorImportService {
    private static final int DEFAULT_PAGE_SIZE = 20;
    private static final int MAX_PAGE_SIZE = 50;
    private static final List<Status> ACTIVE = List.of(Status.PENDING, Status.RUNNING);

    private final AuthorImportJobRepository jobs;
    private final AuthorImportErrorRepository errors;
    private final ImportLock lock;
    private final OpenLibraryDumps dumps;
    private final OpenLibraryImporter openLibrary;
    private final WikidataEnricher wikidata;
    private final AuthorMigration migration;
    private final MongoTemplate mongoTemplate;
    private final TaskExecutor executor;
    private final boolean scheduleEnabled;

    public AuthorImportService(AuthorImportJobRepository jobs, AuthorImportErrorRepository errors, ImportLock lock,
                               OpenLibraryDumps dumps, OpenLibraryImporter openLibrary, WikidataEnricher wikidata,
                               AuthorMigration migration, MongoTemplate mongoTemplate,
                               @Qualifier("authorImportExecutor") TaskExecutor executor,
                               @Value("${authors.import.enabled:false}") boolean scheduleEnabled) {
        this.jobs = jobs;
        this.errors = errors;
        this.lock = lock;
        this.dumps = dumps;
        this.openLibrary = openLibrary;
        this.wikidata = wikidata;
        this.migration = migration;
        this.mongoTemplate = mongoTemplate;
        this.executor = executor;
        this.scheduleEnabled = scheduleEnabled;
    }

    // ---------------------------------------------------------------- admin endpoints

    /** POST /admin/author-import: queues a job and returns it. 409 if one is already running. */
    public AuthorImportJob start(String sourceRaw, String actorId) {
        ImportSource source;
        try {
            source = ImportSource.valueOf(sourceRaw == null ? "" : sourceRaw.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Validation failed",
                    Map.of("source", "Source must be OPEN_LIBRARY or WIKIDATA"));
        }
        if (lock.held() || jobs.existsByStatusIn(ACTIVE)) {
            throw new ApiException(HttpStatus.CONFLICT, "An import is already running");
        }
        AuthorImportJob job = newJob(source, actorId);
        executor.execute(() -> run(job.getId()));
        return job;
    }

    public AdminPage<AuthorImportJob> list(Integer page, Integer pageSize) {
        int p = page == null || page < 0 ? 0 : page;
        int size = pageSize == null || pageSize < 1 ? DEFAULT_PAGE_SIZE : Math.min(pageSize, MAX_PAGE_SIZE);
        long total = jobs.count();
        List<AuthorImportJob> content = mongoTemplate.find(new Query().with(Sort.by(Sort.Direction.DESC, "createdAt"))
                .skip((long) p * size).limit(size), AuthorImportJob.class);
        return AdminPage.of(content, p, size, total);
    }

    public AuthorImportJob get(String id) {
        if (id == null || !ObjectId.isValid(id)) throw notFound();
        return jobs.findById(id).orElseThrow(AuthorImportService::notFound);
    }

    public record ImportErrorView(long line, String sourceId, String message, Date createdAt) {}

    public AdminPage<ImportErrorView> errors(String id, Integer page, Integer pageSize) {
        get(id);
        int p = page == null || page < 0 ? 0 : page;
        int size = pageSize == null || pageSize < 1 ? DEFAULT_PAGE_SIZE : Math.min(pageSize, MAX_PAGE_SIZE);
        Query q = query(where("jobId").is(id));
        long total = mongoTemplate.count(q, AuthorImportError.class);
        List<ImportErrorView> content = mongoTemplate.find(Query.of(q).with(Sort.by(Sort.Direction.ASC, "line"))
                        .skip((long) p * size).limit(size), AuthorImportError.class).stream()
                .map(e -> new ImportErrorView(e.getLine(), e.getSourceId(), e.getMessage(), e.getCreatedAt()))
                .toList();
        return AdminPage.of(content, p, size, total);
    }

    // ---------------------------------------------------------------- schedule and startup

    /** Monthly: imports only when Open Library has published a dump we haven't imported. */
    @Scheduled(cron = "${authors.import.cron:0 0 3 1 * *}", zone = "Africa/Lagos")
    public void monthly() {
        if (!scheduleEnabled) return;
        try {
            if (lock.held() || jobs.existsByStatusIn(ACTIVE)) return;
            OpenLibraryDumps.Latest latest = dumps.resolveLatest();
            if (jobs.existsBySourceAndDatasetVersionAndStatus(ImportSource.OPEN_LIBRARY, latest.datasetVersion(), Status.COMPLETED)) {
                log.info("No newer Open Library dump than {}", latest.datasetVersion());
                return;
            }
            start(ImportSource.OPEN_LIBRARY.name(), null);
        } catch (Exception e) {
            log.warn("Scheduled author import didn't start: {}", e.toString());
        }
    }

    /** Startup: a job left RUNNING or PENDING with no live lock was interrupted; its checkpoint is kept. */
    public void markInterrupted() {
        if (lock.held()) return;
        for (AuthorImportJob job : jobs.findByStatusIn(ACTIVE)) {
            job.setStatus(Status.FAILED);
            job.setErrorSummary("Interrupted: the server restarted. The next run of this dump resumes from the checkpoint.");
            job.setCompletedAt(new Date());
            jobs.save(job);
            log.warn("Author import {} was interrupted by a restart", job.getId());
        }
    }

    // ---------------------------------------------------------------- running

    void run(String jobId) {
        AuthorImportJob job = jobs.findById(jobId).orElse(null);
        if (job == null) return;
        if (!lock.acquire()) {
            finish(job, Status.FAILED, "Another import is running");
            return;
        }
        try {
            execute(job);
            if (job.getSource() == ImportSource.OPEN_LIBRARY && job.getStatus() == Status.COMPLETED && job.getNote() == null) {
                // After a successful Open Library import, enrich from Wikidata (same thread, same lock)
                execute(newJob(ImportSource.WIKIDATA, job.getActorId()));
            }
        } finally {
            lock.release();
        }
    }

    private void execute(AuthorImportJob job) {
        job.setStatus(Status.RUNNING);
        job.setStartedAt(new Date());
        jobs.save(job);
        log.info("Author import {} ({}) started{}", job.getId(), job.getSource(),
                job.getActorId() == null ? " by the schedule" : " by " + job.getActorId());
        try {
            if (job.getSource() == ImportSource.OPEN_LIBRARY) runOpenLibrary(job);
            else runWikidata(job);
            if (job.getStatus() == Status.RUNNING) finish(job, Status.COMPLETED, null);
            log.info("Author import {} completed: read {}, created {}, updated {}, skipped {}, needing review {}, failed {}",
                    job.getId(), job.getRecordsRead(), job.getRecordsCreated(), job.getRecordsUpdated(),
                    job.getRecordsSkipped(), job.getRecordsNeedingReview(), job.getRecordsFailed());
        } catch (Throwable e) {
            if (e instanceof InterruptedException) Thread.currentThread().interrupt();
            log.error("Author import {} failed", job.getId(), e);
            String summary = e.getClass().getSimpleName() + (e.getMessage() == null ? "" : ": " + e.getMessage());
            finish(job, Status.FAILED, summary.length() > 500 ? summary.substring(0, 500) : summary);
        }
    }

    private void runOpenLibrary(AuthorImportJob job) throws Exception {
        OpenLibraryDumps.Latest latest = dumps.resolveLatest();
        job.setDatasetVersion(latest.datasetVersion());
        job.setSourceFile(latest.authors().fileName() + ", " + latest.works().fileName());
        if (jobs.existsBySourceAndDatasetVersionAndStatus(ImportSource.OPEN_LIBRARY, latest.datasetVersion(), Status.COMPLETED)) {
            job.setNote("Open Library dump " + latest.datasetVersion() + " was already imported");
            finish(job, Status.COMPLETED, null);
            return;
        }
        resumeFromPrevious(job, jobs.findFirstBySourceAndDatasetVersionAndStatusOrderByCreatedAtDesc(
                ImportSource.OPEN_LIBRARY, latest.datasetVersion(), Status.FAILED).orElse(null));
        jobs.save(job);

        ImportProgress progress = new ImportProgress(job, jobs, errors, lock);
        Path authorsFile = dumps.download(latest.authors(), progress::heartbeat);
        Path worksFile = dumps.download(latest.works(), progress::heartbeat);

        AuthorImportJob.Checkpoint checkpoint = job.getCheckpoint();
        String pass = checkpoint == null ? OpenLibraryImporter.PASS_WORKS : checkpoint.getPass();
        if (!OpenLibraryImporter.PASS_REDIRECTS.equals(pass)) {
            progress.checkpoint(OpenLibraryImporter.PASS_WORKS, 0);
            WorkCounts works = openLibrary.countWorks(worksFile, progress); // always re-run: the counts live in memory
            long skip = OpenLibraryImporter.PASS_AUTHORS.equals(pass) ? checkpoint.getLineNumber() : 0;
            openLibrary.importAuthors(authorsFile, works, progress, skip);
            pass = OpenLibraryImporter.PASS_REDIRECTS;
            checkpoint = null;
        }
        openLibrary.applyRedirects(authorsFile, progress,
                checkpoint != null && OpenLibraryImporter.PASS_REDIRECTS.equals(checkpoint.getPass()) ? checkpoint.getLineNumber() : 0);

        int duplicates = migration.findPossibleDuplicates();
        job.setRecordsNeedingReview(job.getRecordsNeedingReview() + duplicates);
        progress.save();
        dumps.delete(latest);
    }

    private void runWikidata(AuthorImportJob job) throws Exception {
        // Resume when the last Wikidata run was interrupted
        AuthorImportJob previous = jobs.findFirstBySourceAndIdNotOrderByCreatedAtDesc(ImportSource.WIKIDATA, job.getId()).orElse(null);
        if (previous != null && previous.getStatus() == Status.FAILED) resumeFromPrevious(job, previous);
        jobs.save(job);
        ImportProgress progress = new ImportProgress(job, jobs, errors, lock);
        AuthorImportJob.Checkpoint checkpoint = job.getCheckpoint();
        wikidata.enrich(progress, checkpoint != null && WikidataEnricher.PASS.equals(checkpoint.getPass())
                ? checkpoint.getLineNumber() : 0);
    }

    /** Takes over an interrupted job's checkpoint and counters. */
    private void resumeFromPrevious(AuthorImportJob job, AuthorImportJob previous) {
        if (previous == null || previous.getCheckpoint() == null || previous.getId().equals(job.getId())) return;
        job.setCheckpoint(previous.getCheckpoint());
        job.setRecordsRead(previous.getRecordsRead());
        job.setRecordsCreated(previous.getRecordsCreated());
        job.setRecordsUpdated(previous.getRecordsUpdated());
        job.setRecordsSkipped(previous.getRecordsSkipped());
        job.setRecordsNeedingReview(previous.getRecordsNeedingReview());
        job.setRecordsFailed(previous.getRecordsFailed());
        job.setNote("Resumed from job " + previous.getId() + " at " + previous.getCheckpoint().getPass()
                + " line " + previous.getCheckpoint().getLineNumber());
        previous.setCheckpoint(null); // only one job resumes it
        previous.setNote("Resumed by job " + job.getId());
        jobs.save(previous);
        log.info("Author import {} resumes job {} from {} line {}", job.getId(), previous.getId(),
                job.getCheckpoint().getPass(), job.getCheckpoint().getLineNumber());
    }

    private AuthorImportJob newJob(ImportSource source, String actorId) {
        AuthorImportJob job = new AuthorImportJob();
        job.setSource(source);
        job.setStatus(Status.PENDING);
        job.setActorId(actorId);
        job.setCreatedAt(new Date());
        return jobs.insert(job);
    }

    private void finish(AuthorImportJob job, Status status, String errorSummary) {
        job.setStatus(status);
        job.setErrorSummary(errorSummary);
        job.setCompletedAt(new Date());
        jobs.save(job);
    }

    private static ResponseStatusException notFound() {
        return new ResponseStatusException(HttpStatus.NOT_FOUND, "Import not found");
    }
}

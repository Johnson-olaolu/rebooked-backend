package com.personal.rebooked.author.importer;

import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.mongodb.bulk.BulkWriteError;
import com.personal.rebooked.author.AuthorAuditLog;
import com.personal.rebooked.author.AuthorNames;
import com.personal.rebooked.author.AuthorService;
import com.personal.rebooked.author.admin.AuthorMergeService;
import com.personal.rebooked.author.models.Author;
import com.personal.rebooked.author.models.Author.Source;
import com.personal.rebooked.author.models.AuthorExternalIdentifier;
import com.personal.rebooked.author.models.AuthorExternalIdentifier.IdSource;
import com.personal.rebooked.author.repositories.AuthorExternalIdentifierRepository;
import com.personal.rebooked.utils.Misc;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.bson.types.ObjectId;
import org.springframework.data.mongodb.BulkOperationException;
import org.springframework.data.mongodb.core.BulkOperations;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.stereotype.Component;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.zip.GZIPInputStream;

import static org.springframework.data.mongodb.core.query.Criteria.where;
import static org.springframework.data.mongodb.core.query.Query.query;

/**
 * The Open Library passes (§23g): count works per author, import authors in batches of 1,000, then apply Open
 * Library's redirects and deletes. Re-running a dump creates no duplicates: authors are found through their
 * OPEN_LIBRARY identifier, which has a unique index.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class OpenLibraryImporter {
    static final int BATCH = 1000;
    public static final String PASS_WORKS = "WORKS";
    public static final String PASS_AUTHORS = "AUTHORS";
    public static final String PASS_REDIRECTS = "REDIRECTS";

    private final MongoTemplate mongoTemplate;
    private final AuthorService authorService;
    private final AuthorMergeService mergeService;
    private final AuthorExternalIdentifierRepository identifiers;
    private final ObjectMapper mapper = new ObjectMapper();
    private final JsonFactory jsonFactory = new JsonFactory();

    // ---------------------------------------------------------------- works

    public WorkCounts countWorks(Path worksFile, ImportProgress progress) throws IOException {
        WorkCounts counts = new WorkCounts();
        Set<Long> seen = new HashSet<>();
        long lineNumber = 0;
        try (BufferedReader reader = gzipReader(worksFile)) {
            String line;
            while ((line = reader.readLine()) != null) {
                if (++lineNumber % 100_000 == 0) progress.heartbeat();
                if (!line.startsWith("/type/work\t") || !line.contains("/authors/")) continue;
                OpenLibraryLines.Line parsed = OpenLibraryLines.split(line);
                if (parsed == null) continue;
                seen.clear();
                try {
                    OpenLibraryLines.workAuthorKeys(jsonFactory, parsed.json(), key -> {
                        long number = OpenLibraryLines.olNumber(key);
                        if (number > 0 && seen.add(number)) counts.add(number); // each author once per work
                    });
                } catch (IOException e) {
                    // A malformed work only loses its counts
                }
            }
        }
        log.info("Works pass: {} lines read", lineNumber);
        return counts;
    }

    // ---------------------------------------------------------------- authors

    private record Pending(OpenLibraryLines.AuthorRecord record, long line) {}

    /** Imports /type/author records, skipping lines up to skipUntil (a resumed run). */
    public void importAuthors(Path authorsFile, WorkCounts works, ImportProgress progress, long skipUntil) throws IOException {
        List<Pending> batch = new ArrayList<>(BATCH);
        long lineNumber = 0;
        try (BufferedReader reader = gzipReader(authorsFile)) {
            String line;
            while ((line = reader.readLine()) != null) {
                lineNumber++;
                if (lineNumber <= skipUntil) continue;
                progress.read();
                if (!line.startsWith("/type/author\t")) continue;
                OpenLibraryLines.Line parsed = OpenLibraryLines.split(line);
                if (parsed == null) {
                    progress.failed(lineNumber, null, "Malformed line");
                    continue;
                }
                OpenLibraryLines.AuthorRecord record;
                try {
                    record = OpenLibraryLines.author(mapper, parsed.key(), parsed.json());
                } catch (Exception e) {
                    progress.failed(lineNumber, OpenLibraryLines.olId(parsed.key()), "Unreadable record: " + e.getMessage());
                    continue;
                }
                int workCount = works.get(OpenLibraryLines.olNumber(parsed.key()));
                // Selection policy: at least 2 works, or a strong identifier
                if (record.name() == null || record.olId() == null || (workCount < 2 && !record.hasStrongId())) {
                    progress.skipped();
                    continue;
                }
                batch.add(new Pending(record.withWorkCount(workCount), lineNumber));
                if (batch.size() >= BATCH) {
                    writeBatch(batch, progress);
                    batch.clear();
                    progress.checkpoint(PASS_AUTHORS, lineNumber);
                }
            }
        }
        if (!batch.isEmpty()) writeBatch(batch, progress);
        progress.checkpoint(PASS_AUTHORS, lineNumber);
    }

    private void writeBatch(List<Pending> batch, ImportProgress progress) {
        // Who we already have, through their Open Library ids (one $in)
        Map<String, String> authorIdByOl = new HashMap<>();
        for (AuthorExternalIdentifier id : identifiers.findBySourceAndSourceIdIn(IdSource.OPEN_LIBRARY,
                batch.stream().map(p -> p.record().olId()).toList())) {
            authorIdByOl.put(id.getSourceId(), id.getAuthorId());
        }
        Map<String, Author> existing = new HashMap<>();
        if (!authorIdByOl.isEmpty()) {
            for (Author a : mongoTemplate.find(query(where("id").in(new HashSet<>(authorIdByOl.values()))), Author.class)) {
                existing.put(a.getId(), a);
            }
        }
        // Owners of the strong ids in this batch, to spot conflicts
        Map<IdSource, Map<String, String>> owners = new EnumMap<>(IdSource.class);
        for (IdSource source : List.of(IdSource.WIKIDATA, IdSource.VIAF, IdSource.ISNI)) {
            List<String> ids = batch.stream().map(p -> p.record().strongIds().get(source)).filter(Objects::nonNull).toList();
            Map<String, String> bySourceId = new HashMap<>();
            if (!ids.isEmpty()) {
                identifiers.findBySourceAndSourceIdIn(source, ids).forEach(i -> bySourceId.put(i.getSourceId(), i.getAuthorId()));
            }
            owners.put(source, bySourceId);
        }

        List<Author> inserts = new ArrayList<>();
        Map<String, Long> insertLines = new HashMap<>();
        List<AuthorExternalIdentifier> newIds = new ArrayList<>();
        BulkOperations updates = mongoTemplate.bulkOps(BulkOperations.BulkMode.UNORDERED, Author.class);
        int updateCount = 0;
        List<String> renamed = new ArrayList<>();
        // An author can hold several Open Library ids after a merge; only one record may write its fields
        Map<String, String> owner = new HashMap<>();

        for (Pending pending : batch) {
            OpenLibraryLines.AuthorRecord record = pending.record();
            Author current = existing.get(authorIdByOl.get(record.olId()));
            if (current != null) {
                if (current.getStatus() == Author.Status.MERGED) {
                    progress.skipped();
                    continue;
                }
                String primary = owner.computeIfAbsent(current.getId(), id -> primaryOlId(current, record.olId()));
                if (!primary.equals(record.olId())) {
                    progress.skipped(); // a record merged into this author: the primary one owns the fields
                    continue;
                }
                Update update = changes(current, record);
                if (update == null) {
                    progress.skipped(); // unchanged since the last import
                } else {
                    updates.updateOne(query(where("id").is(current.getId())), update);
                    updateCount++;
                    progress.updated();
                    if (update.modifies("name")) renamed.add(current.getId());
                }
                linkStrongIds(current.getId(), record, owners, newIds, progress, pending.line());
            } else {
                Author created = newAuthor(record);
                newIds.add(new AuthorExternalIdentifier(created.getId(), IdSource.OPEN_LIBRARY, record.olId(), Source.OPEN_LIBRARY));
                if (linkStrongIds(created.getId(), record, owners, newIds, progress, pending.line())) {
                    created.setStatus(Author.Status.NEEDS_REVIEW);
                }
                inserts.add(created);
                insertLines.put(created.getId(), pending.line());
            }
        }

        Set<String> failedInserts = insertAuthors(inserts, insertLines, progress);
        if (updateCount > 0) {
            try {
                updates.execute();
            } catch (BulkOperationException e) {
                for (BulkWriteError error : e.getErrors()) progress.failed(0, null, "Update failed: " + error.getMessage());
            }
        }
        newIds.removeIf(id -> failedInserts.contains(id.getAuthorId()));
        insertIdentifiers(newIds, progress);
        for (String id : renamed) {
            Author author = mongoTemplate.findById(id, Author.class);
            if (author != null) authorService.refreshBookCache(author);
        }
    }

    /**
     * The $set for an existing author, or null when nothing changed. Never overwrites a field whose source isn't
     * Open Library: admin edits and seller-entered names stay.
     */
    Update changes(Author a, OpenLibraryLines.AuthorRecord r) {
        Update update = new Update();
        boolean changed = false;
        String name = a.getName();
        if (ours(a.fieldSource("name")) && !r.name().equals(a.getName())) {
            name = r.name();
            update.set("name", name).set("normalizedName", AuthorNames.normalize(name)).set("fieldSources.name", Source.OPEN_LIBRARY);
            changed = true;
        }
        List<Author.Alias> aliases = mergeAliases(a.aliasList(), r.aliases(), name, Source.OPEN_LIBRARY);
        boolean aliasesChanged = !aliases.equals(a.aliasList());
        if (aliasesChanged) {
            update.set("aliases", aliases);
            changed = true;
        }
        if (changed) update.set("searchKeys", AuthorNames.searchKeys(name, aliases.stream().map(Author.Alias::getName).toList()));

        changed |= setIfOurs(update, a, "biography", a.getBiography(), r.biography());
        changed |= setIfOurs(update, a, "birthYear", a.getBirthYear(), r.birthYear());
        changed |= setIfOurs(update, a, "deathYear", a.getDeathYear(), r.deathYear());
        boolean photoOurs = a.getPhoto() == null || a.getPhoto().getSource() == Source.OPEN_LIBRARY;
        String currentPhoto = a.getPhoto() == null ? null : a.getPhoto().getUrl();
        if (photoOurs && !Objects.equals(currentPhoto, r.photoUrl())) {
            update.set("photo", r.photoUrl() == null ? null : new Author.Photo(r.photoUrl(), Source.OPEN_LIBRARY));
            update.set("fieldSources.photo", Source.OPEN_LIBRARY);
            changed = true;
        }
        if (a.getWorkCount() != r.workCount()) {
            update.set("workCount", r.workCount());
            changed = true;
        }
        String olLink = IdSource.OPEN_LIBRARY.url(r.olId());
        if (a.getLinks() == null || a.getLinks().getOpenLibrary() == null) {
            update.set("links.openLibrary", olLink);
            changed = true;
        }
        if (!changed) return null;
        return update.set("updatedAt", new Date());
    }

    /**
     * The Open Library record that owns an author's fields: the one in links.openLibrary (where it was imported
     * from), or {@code fallback} when there's none yet.
     */
    static String primaryOlId(Author a, String fallback) {
        String link = a.getLinks() == null ? null : a.getLinks().getOpenLibrary();
        if (link != null) {
            String id = IdSource.OPEN_LIBRARY.normalize(link.substring(link.lastIndexOf('/') + 1));
            if (id != null) return id;
        }
        return fallback;
    }

    private static boolean ours(Source source) {
        return source == null || source == Source.OPEN_LIBRARY;
    }

    private static boolean setIfOurs(Update update, Author a, String field, Object current, Object incoming) {
        if (!ours(a.fieldSource(field)) || Objects.equals(current, incoming)) return false;
        // Don't claim an empty field for Open Library just because both are empty
        if (current == null && incoming == null) return false;
        update.set(field, incoming).set("fieldSources." + field, Source.OPEN_LIBRARY);
        return true;
    }

    /**
     * Replaces the aliases from {@code source}; keeps everyone else's. Skips the name and duplicates. Existing aliases
     * keep their place and new ones go at the end, so re-running an import (or alternating Open Library and Wikidata)
     * gives the same list and isn't counted as a change.
     */
    static List<Author.Alias> mergeAliases(List<Author.Alias> current, List<String> incoming, String name, Source source) {
        Map<String, String> wanted = new LinkedHashMap<>();
        for (String value : incoming) wanted.putIfAbsent(AuthorNames.normalize(value), value);
        List<Author.Alias> result = new ArrayList<>();
        Set<String> seen = new HashSet<>();
        seen.add(AuthorNames.normalize(name));
        for (Author.Alias alias : current) {
            if (alias.getSource() == source) {
                // Still in the source: keep its place, with the source's current spelling
                String value = wanted.get(alias.getNormalizedName());
                if (value != null && seen.add(alias.getNormalizedName())) {
                    result.add(value.equals(alias.getName()) ? alias : new Author.Alias(value, alias.getNormalizedName(), source));
                }
            } else if (seen.add(alias.getNormalizedName())) {
                result.add(alias);
            }
        }
        wanted.forEach((key, value) -> {
            if (seen.add(key)) result.add(new Author.Alias(value, key, source));
        });
        return result;
    }

    private Author newAuthor(OpenLibraryLines.AuthorRecord r) {
        Author a = new Author();
        a.setId(new ObjectId().toHexString()); // known before the bulk insert, for the identifiers
        a.setName(r.name());
        a.setNormalizedName(AuthorNames.normalize(r.name()));
        a.setAliases(mergeAliases(List.of(), r.aliases(), r.name(), Source.OPEN_LIBRARY));
        a.setSearchKeys(AuthorNames.searchKeys(r.name(), a.aliasNames()));
        a.setBiography(r.biography());
        a.setBirthYear(r.birthYear());
        a.setDeathYear(r.deathYear());
        if (r.photoUrl() != null) a.setPhoto(new Author.Photo(r.photoUrl(), Source.OPEN_LIBRARY));
        a.setLinks(new Author.Links(IdSource.OPEN_LIBRARY.url(r.olId()), null, null));
        a.setWorkCount(r.workCount());
        a.setOrigin(Author.Origin.IMPORTED);
        a.setStatus(Author.Status.VERIFIED); // a source backs it
        a.getFieldSources().put("name", Source.OPEN_LIBRARY);
        if (r.biography() != null) a.getFieldSources().put("biography", Source.OPEN_LIBRARY);
        if (r.birthYear() != null) a.getFieldSources().put("birthYear", Source.OPEN_LIBRARY);
        if (r.deathYear() != null) a.getFieldSources().put("deathYear", Source.OPEN_LIBRARY);
        if (r.photoUrl() != null) a.getFieldSources().put("photo", Source.OPEN_LIBRARY);
        Date now = new Date();
        a.setCreatedAt(now);
        a.setUpdatedAt(now);
        return a;
    }

    /**
     * Queues the record's Wikidata, VIAF and ISNI ids for the author. An id that already belongs to another author
     * isn't linked; it's logged and counted as needing review. Returns whether there was such a conflict.
     */
    private boolean linkStrongIds(String authorId, OpenLibraryLines.AuthorRecord record,
                                  Map<IdSource, Map<String, String>> owners, List<AuthorExternalIdentifier> newIds,
                                  ImportProgress progress, long line) {
        boolean conflict = false;
        for (Map.Entry<IdSource, String> id : record.strongIds().entrySet()) {
            String owner = owners.get(id.getKey()).get(id.getValue());
            if (owner == null) {
                newIds.add(new AuthorExternalIdentifier(authorId, id.getKey(), id.getValue(), Source.OPEN_LIBRARY));
                owners.get(id.getKey()).put(id.getValue(), authorId); // a second record in this batch now conflicts
            } else if (!owner.equals(authorId)) {
                conflict = true;
                progress.needsReview();
                progress.note(line, record.olId(), id.getKey() + " " + id.getValue() + " is already linked to author " + owner);
            }
        }
        return conflict;
    }

    /** Bulk insert with slugs; returns the ids of authors that failed. */
    private Set<String> insertAuthors(List<Author> authors, Map<String, Long> lines, ImportProgress progress) {
        if (authors.isEmpty()) return Set.of();
        assignSlugs(authors);
        Set<String> failed = new HashSet<>();
        try {
            mongoTemplate.bulkOps(BulkOperations.BulkMode.UNORDERED, Author.class).insert(authors).execute();
        } catch (BulkOperationException e) {
            for (BulkWriteError error : e.getErrors()) {
                Author author = authors.get(error.getIndex());
                failed.add(author.getId());
                progress.failed(lines.getOrDefault(author.getId(), 0L), null,
                        "Couldn't create \"" + author.getName() + "\": " + error.getMessage());
            }
        }
        for (Author author : authors) if (!failed.contains(author.getId())) progress.created();
        return failed;
    }

    private void insertIdentifiers(List<AuthorExternalIdentifier> ids, ImportProgress progress) {
        if (ids.isEmpty()) return;
        try {
            mongoTemplate.bulkOps(BulkOperations.BulkMode.UNORDERED, AuthorExternalIdentifier.class).insert(ids).execute();
        } catch (BulkOperationException e) {
            for (BulkWriteError error : e.getErrors()) {
                AuthorExternalIdentifier id = ids.get(error.getIndex());
                progress.note(0, id.getSourceId(), "Identifier not linked: " + error.getMessage());
            }
        }
    }

    /**
     * Unique slugs for a batch: one $in for the base slugs, and a suffix search only for bases that collide
     * (already taken, or used twice in the batch).
     */
    private void assignSlugs(List<Author> authors) {
        Map<String, Integer> uses = new HashMap<>();
        List<String> bases = new ArrayList<>();
        for (Author a : authors) {
            String base = Misc.slugify(a.getName(), "author");
            bases.add(base);
            uses.merge(base, 1, Integer::sum);
        }
        Query takenQuery = query(where("slug").in(new HashSet<>(bases)));
        takenQuery.fields().include("slug");
        Set<String> taken = new HashSet<>();
        mongoTemplate.find(takenQuery, Author.class).forEach(a -> taken.add(a.getSlug()));

        Map<String, Long> nextSuffix = new HashMap<>();
        for (String base : new HashSet<>(bases)) {
            if (taken.contains(base) || uses.get(base) > 1) nextSuffix.put(base, maxSuffix(base) + 1);
        }
        Set<String> used = new HashSet<>();
        for (int i = 0; i < authors.size(); i++) {
            String base = bases.get(i);
            if (!taken.contains(base) && used.add(base)) {
                authors.get(i).setSlug(base);
            } else {
                long n = nextSuffix.merge(base, 1L, Long::sum) - 1;
                authors.get(i).setSlug(base + "-" + n);
            }
        }
    }

    /** The highest -N on slugs starting with base; 1 when there's none. */
    private long maxSuffix(String base) {
        Query q = query(where("slug").regex("^" + base + "-[0-9]+$"));
        q.fields().include("slug");
        Pattern suffix = Pattern.compile("-(\\d{1,9})$");
        long max = 1;
        for (Author a : mongoTemplate.find(q, Author.class)) {
            Matcher m = suffix.matcher(a.getSlug());
            if (m.find()) max = Math.max(max, Long.parseLong(m.group(1)));
        }
        return max;
    }

    // ---------------------------------------------------------------- redirects and deletes

    private record Redirect(String from, String to, long line) {}

    /**
     * Open Library merged two of its authors (/type/redirect, location = the survivor): if we have both, merge ours;
     * if we only have the old one, re-point its identifier. /type/delete removes the identifier only: an author a
     * book uses is never deleted.
     */
    public void applyRedirects(Path authorsFile, ImportProgress progress, long skipUntil) throws IOException {
        List<Redirect> redirects = new ArrayList<>(BATCH);
        List<String> deletes = new ArrayList<>(BATCH);
        long lineNumber = 0;
        try (BufferedReader reader = gzipReader(authorsFile)) {
            String line;
            while ((line = reader.readLine()) != null) {
                lineNumber++;
                if (lineNumber <= skipUntil) continue;
                if (line.startsWith("/type/redirect\t")) {
                    OpenLibraryLines.Line parsed = OpenLibraryLines.split(line);
                    if (parsed == null || OpenLibraryLines.olNumber(parsed.key()) < 0) continue;
                    try {
                        String location = OpenLibraryLines.redirectLocation(mapper, parsed.json());
                        if (OpenLibraryLines.olNumber(location) > 0) {
                            redirects.add(new Redirect(OpenLibraryLines.olId(parsed.key()), OpenLibraryLines.olId(location), lineNumber));
                        }
                    } catch (IOException e) {
                        progress.failed(lineNumber, OpenLibraryLines.olId(parsed.key()), "Unreadable redirect");
                    }
                } else if (line.startsWith("/type/delete\t")) {
                    OpenLibraryLines.Line parsed = OpenLibraryLines.split(line);
                    if (parsed != null && OpenLibraryLines.olNumber(parsed.key()) > 0) deletes.add(OpenLibraryLines.olId(parsed.key()));
                }
                if (redirects.size() >= BATCH || deletes.size() >= BATCH) {
                    applyRedirectBatch(redirects, progress);
                    applyDeleteBatch(deletes);
                    redirects.clear();
                    deletes.clear();
                    progress.checkpoint(PASS_REDIRECTS, lineNumber);
                } else if (lineNumber % 100_000 == 0) {
                    progress.heartbeat();
                }
            }
        }
        applyRedirectBatch(redirects, progress);
        applyDeleteBatch(deletes);
        progress.checkpoint(PASS_REDIRECTS, lineNumber);
    }

    private void applyRedirectBatch(List<Redirect> redirects, ImportProgress progress) {
        if (redirects.isEmpty()) return;
        Map<String, AuthorExternalIdentifier> old = new HashMap<>();
        identifiers.findBySourceAndSourceIdIn(IdSource.OPEN_LIBRARY, redirects.stream().map(Redirect::from).toList())
                .forEach(i -> old.put(i.getSourceId(), i));
        if (old.isEmpty()) return; // none of these were ours
        Map<String, String> survivors = new HashMap<>();
        identifiers.findBySourceAndSourceIdIn(IdSource.OPEN_LIBRARY,
                        redirects.stream().filter(r -> old.containsKey(r.from())).map(Redirect::to).toList())
                .forEach(i -> survivors.put(i.getSourceId(), i.getAuthorId()));

        for (Redirect redirect : redirects) {
            AuthorExternalIdentifier oldId = old.get(redirect.from());
            if (oldId == null) continue;
            String survivorId = survivors.get(redirect.to());
            try {
                if (survivorId == null) {
                    // Only the old one is ours: it simply has a new Open Library id
                    mongoTemplate.updateFirst(query(where("id").is(oldId.getId())),
                            Update.update("sourceId", redirect.to()), AuthorExternalIdentifier.class);
                    progress.updated();
                } else if (!survivorId.equals(oldId.getAuthorId())) {
                    Author from = authorService.findCanonical(oldId.getAuthorId()).orElse(null);
                    Author into = authorService.findCanonical(survivorId).orElse(null);
                    if (from != null && into != null && !from.getId().equals(into.getId())) {
                        mergeService.merge(from.getId(), into.getId(), "Open Library redirect", AuthorAuditLog.SYSTEM);
                        progress.updated();
                    }
                }
            } catch (Exception e) {
                progress.failed(redirect.line(), redirect.from(), "Redirect to " + redirect.to() + " failed: " + e.getMessage());
            }
        }
    }

    private void applyDeleteBatch(List<String> deletedOlIds) {
        if (deletedOlIds.isEmpty()) return;
        mongoTemplate.remove(query(where("source").is(IdSource.OPEN_LIBRARY).and("sourceId").in(deletedOlIds)),
                AuthorExternalIdentifier.class);
    }

    private static BufferedReader gzipReader(Path file) throws IOException {
        return new BufferedReader(new InputStreamReader(
                new GZIPInputStream(Files.newInputStream(file), 1 << 16), StandardCharsets.UTF_8), 1 << 16);
    }
}

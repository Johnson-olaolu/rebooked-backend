package com.personal.rebooked.author.importer;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.personal.rebooked.author.AuthorNames;
import com.personal.rebooked.author.models.Author;
import com.personal.rebooked.author.models.Author.Source;
import com.personal.rebooked.author.models.AuthorExternalIdentifier;
import com.personal.rebooked.author.models.AuthorExternalIdentifier.IdSource;
import com.personal.rebooked.utils.Misc;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import static org.springframework.data.mongodb.core.query.Criteria.where;
import static org.springframework.data.mongodb.core.query.Query.query;

/**
 * Wikidata enrichment (§23h). Only authors whose Wikidata id came from Open Library: that link is strong evidence,
 * and there's no name matching across sources. Writes only fields that are empty or that Wikidata set before.
 */
@Slf4j
@Component
public class WikidataEnricher {
    public static final String PASS = "WIKIDATA";
    static final int BATCH = 50;
    private static final int MAX_ALIASES = 20;
    private static final int MAX_CONSECUTIVE_FAILURES = 5;
    private static final Pattern TIME = Pattern.compile("^([+-])0*(\\d{1,9})-");

    private final MongoTemplate mongoTemplate;
    private final String apiUrl;
    private final String userAgent;
    private final Duration interval;
    private final ObjectMapper mapper = new ObjectMapper();
    private final HttpClient http = HttpClient.newBuilder()
            .followRedirects(HttpClient.Redirect.NORMAL).connectTimeout(Duration.ofSeconds(20)).build();

    public WikidataEnricher(MongoTemplate mongoTemplate,
                            @Value("${wikidata.api-url:https://www.wikidata.org/w/api.php}") String apiUrl,
                            @Value("${wikidata.user-agent:RebookedAuthorImporter/1.0}") String userAgent,
                            @Value("${wikidata.request-interval:PT1S}") Duration interval) {
        this.mongoTemplate = mongoTemplate;
        this.apiUrl = apiUrl;
        this.userAgent = userAgent;
        this.interval = interval;
    }

    /** Enriches every eligible author, skipping the first {@code skip} identifiers (a resumed run). */
    public void enrich(ImportProgress progress, long skip) throws IOException, InterruptedException {
        if (!userAgent.contains("@") && !userAgent.contains("http")) {
            log.warn("wikidata.user-agent has no contact address; Wikimedia asks for one (set WIKIDATA_USER_AGENT)");
        }
        Query eligible = query(where("source").is(IdSource.WIKIDATA).and("addedBy").is(Source.OPEN_LIBRARY))
                .with(Sort.by(Sort.Direction.ASC, "id"));
        long position = 0;
        int consecutiveFailures = 0;
        List<AuthorExternalIdentifier> batch = new ArrayList<>(BATCH);
        try (Stream<AuthorExternalIdentifier> ids = mongoTemplate.stream(eligible, AuthorExternalIdentifier.class)) {
            Iterator<AuthorExternalIdentifier> it = ids.iterator();
            while (it.hasNext()) {
                AuthorExternalIdentifier id = it.next();
                position++;
                if (position <= skip) continue;
                batch.add(id);
                if (batch.size() >= BATCH || !it.hasNext()) {
                    consecutiveFailures = processBatch(batch, progress, position) ? 0 : consecutiveFailures + 1;
                    if (consecutiveFailures >= MAX_CONSECUTIVE_FAILURES) {
                        throw new IOException("Wikidata failed " + MAX_CONSECUTIVE_FAILURES + " times in a row; stopping");
                    }
                    batch.clear();
                    progress.checkpoint(PASS, position);
                    if (!interval.isZero() && it.hasNext()) Thread.sleep(interval.toMillis());
                }
            }
        }
        progress.job().setCoverage(coverage());
        progress.save();
    }

    /** Returns false when the request itself failed (after retries). */
    private boolean processBatch(List<AuthorExternalIdentifier> batch, ImportProgress progress, long position)
            throws InterruptedException {
        Map<String, JsonNode> entities;
        try {
            entities = fetch(batch.stream().map(AuthorExternalIdentifier::getSourceId).distinct().toList());
        } catch (IOException e) {
            for (AuthorExternalIdentifier id : batch) progress.failed(position, id.getSourceId(), "Wikidata request failed: " + e.getMessage());
            return false;
        }
        Map<String, Author> authors = new HashMap<>();
        mongoTemplate.find(query(where("id").in(batch.stream().map(AuthorExternalIdentifier::getAuthorId).toList())), Author.class)
                .forEach(a -> authors.put(a.getId(), a));
        for (AuthorExternalIdentifier id : batch) {
            progress.read();
            Author author = authors.get(id.getAuthorId());
            JsonNode entity = entities.get(id.getSourceId());
            if (author == null || author.getStatus() == Author.Status.MERGED || entity == null || entity.has("missing")) {
                progress.skipped();
                continue;
            }
            try {
                Update update = changes(author, Facts.from(entity, id.getSourceId()));
                if (update == null) {
                    progress.skipped();
                } else {
                    mongoTemplate.updateFirst(query(where("id").is(author.getId())), update, Author.class);
                    progress.updated();
                }
            } catch (Exception e) {
                progress.failed(position, id.getSourceId(), "Couldn't apply Wikidata data: " + e.getMessage());
            }
        }
        return true;
    }

    /** One wbgetentities call; entities by the requested id (a redirected id maps to its target's entity). */
    Map<String, JsonNode> fetch(List<String> qids) throws IOException, InterruptedException {
        String url = apiUrl + "?action=wbgetentities&format=json&maxlag=5&languages=en&sitefilter=enwiki"
                + "&props=" + URLEncoder.encode("labels|descriptions|aliases|claims|sitelinks", StandardCharsets.UTF_8)
                + "&ids=" + URLEncoder.encode(String.join("|", qids), StandardCharsets.UTF_8);
        for (int attempt = 1; ; attempt++) {
            HttpResponse<String> response = http.send(HttpRequest.newBuilder(URI.create(url))
                    .header("User-Agent", userAgent).header("Accept", "application/json")
                    .timeout(Duration.ofSeconds(60)).GET().build(), HttpResponse.BodyHandlers.ofString());
            JsonNode body = response.statusCode() == 200 ? mapper.readTree(response.body()) : null;
            boolean lagged = body != null && "maxlag".equals(body.path("error").path("code").asText());
            boolean retry = lagged || response.statusCode() == 429 || response.statusCode() >= 500;
            if (retry && attempt < 4) {
                long wait = response.headers().firstValue("retry-after").map(WikidataEnricher::seconds).orElse(5L * attempt);
                Thread.sleep(Math.min(wait, 60) * 1000);
                continue;
            }
            if (body == null) throw new IOException("HTTP " + response.statusCode());
            if (body.has("error")) throw new IOException(body.path("error").path("info").asText("Wikidata error"));
            Map<String, JsonNode> byId = new HashMap<>();
            body.path("entities").fields().forEachRemaining(e -> {
                byId.put(e.getKey(), e.getValue());
                JsonNode from = e.getValue().path("redirects").path("from");
                if (from.isTextual()) byId.put(from.asText(), e.getValue());
            });
            return byId;
        }
    }

    private static long seconds(String header) {
        try {
            return Math.max(1, Long.parseLong(header.trim()));
        } catch (NumberFormatException e) {
            return 5;
        }
    }

    /** What we take from one Wikidata entity. */
    record Facts(String qid, String description, List<String> aliases, Integer birthYear, Integer deathYear,
                 List<String> countries, String wikipedia, String photoUrl) {

        static Facts from(JsonNode entity, String requestedId) {
            String qid = entity.path("id").asText(requestedId);
            String description = Misc.stripHtml(entity.path("descriptions").path("en").path("value").asText(null));
            if (description != null && description.length() > 200) description = description.substring(0, 200);

            List<String> aliases = new ArrayList<>();
            for (JsonNode alias : entity.path("aliases").path("en")) {
                String value = AuthorNames.clean(alias.path("value").asText(null));
                if (value != null && value.length() >= 2 && value.length() <= 200 && aliases.size() < MAX_ALIASES) aliases.add(value);
            }

            List<String> countries = new ArrayList<>();
            for (JsonNode claim : claims(entity, "P27")) {
                String code = WikidataCountries.iso(claim.path("mainsnak").path("datavalue").path("value").path("id").asText(null));
                if (code != null && !countries.contains(code)) countries.add(code);
            }

            String wikipedia = null;
            String title = entity.path("sitelinks").path("enwiki").path("title").asText(null);
            if (title != null) wikipedia = "https://en.wikipedia.org/wiki/" + encodePath(title.replace(' ', '_'));

            String photoUrl = null;
            List<JsonNode> images = claims(entity, "P18");
            if (!images.isEmpty()) {
                String file = images.get(0).path("mainsnak").path("datavalue").path("value").asText(null);
                if (file != null) photoUrl = "https://commons.wikimedia.org/wiki/Special:FilePath/" + encodePath(file.replace(' ', '_'));
            }
            return new Facts(qid, description, aliases, year(claims(entity, "P569")), year(claims(entity, "P570")),
                    countries, wikipedia, photoUrl);
        }

        /** Non-deprecated statements, preferred ones first. */
        private static List<JsonNode> claims(JsonNode entity, String property) {
            List<JsonNode> preferred = new ArrayList<>();
            List<JsonNode> normal = new ArrayList<>();
            for (JsonNode claim : entity.path("claims").path(property)) {
                String rank = claim.path("rank").asText("normal");
                if ("preferred".equals(rank)) preferred.add(claim);
                else if (!"deprecated".equals(rank)) normal.add(claim);
            }
            preferred.addAll(normal);
            return preferred;
        }

        /** "+1930-11-16T00:00:00Z" → 1930; "-0500-…" → -500. */
        private static Integer year(List<JsonNode> claims) {
            for (JsonNode claim : claims) {
                String time = claim.path("mainsnak").path("datavalue").path("value").path("time").asText(null);
                if (time == null) continue;
                Matcher m = TIME.matcher(time);
                if (m.find()) {
                    int year = Integer.parseInt(m.group(2));
                    return "-".equals(m.group(1)) ? -year : year;
                }
            }
            return null;
        }

        private static String encodePath(String value) {
            return URLEncoder.encode(value, StandardCharsets.UTF_8).replace("+", "%20").replace("%2F", "/");
        }
    }

    /** The $set for one author, or null when nothing changes. */
    static Update changes(Author a, Facts f) {
        Update update = new Update();
        boolean changed = false;

        if (f.description() != null && open(a.getDescription(), a.fieldSource("description"))
                && !f.description().equals(a.getDescription())) {
            update.set("description", f.description()).set("fieldSources.description", Source.WIKIDATA);
            changed = true;
        }
        if (f.birthYear() != null && a.getBirthYear() == null) {
            update.set("birthYear", f.birthYear()).set("fieldSources.birthYear", Source.WIKIDATA);
            changed = true;
        }
        if (f.deathYear() != null && a.getDeathYear() == null) {
            update.set("deathYear", f.deathYear()).set("fieldSources.deathYear", Source.WIKIDATA);
            changed = true;
        }
        List<Author.Alias> aliases = OpenLibraryImporter.mergeAliases(a.aliasList(), f.aliases(), a.getName(), Source.WIKIDATA);
        if (!aliases.equals(a.aliasList())) {
            update.set("aliases", aliases)
                    .set("searchKeys", AuthorNames.searchKeys(a.getName(), aliases.stream().map(Author.Alias::getName).toList()));
            changed = true;
        }
        List<String> currentCountries = a.getCountries() == null ? List.of() : a.getCountries();
        if (!f.countries().isEmpty() && (currentCountries.isEmpty() || a.fieldSource("countries") == Source.WIKIDATA)
                && !f.countries().equals(currentCountries)) {
            update.set("countries", f.countries()).set("fieldSources.countries", Source.WIKIDATA);
            changed = true;
        }
        Author.Links links = a.getLinks() == null ? new Author.Links() : a.getLinks();
        String wikidataUrl = IdSource.WIKIDATA.url(f.qid());
        if (links.getWikidata() == null) {
            update.set("links.wikidata", wikidataUrl);
            changed = true;
        }
        if (f.wikipedia() != null && !f.wikipedia().equals(links.getWikipedia())) {
            update.set("links.wikipedia", f.wikipedia());
            changed = true;
        }
        // Stored, but hidden by the licensing gate until the reuse terms are reviewed
        boolean photoOpen = a.getPhoto() == null || a.getPhoto().getSource() == Source.WIKIDATA;
        if (f.photoUrl() != null && photoOpen && Misc.isHttpsUrl(f.photoUrl())
                && (a.getPhoto() == null || !f.photoUrl().equals(a.getPhoto().getUrl()))) {
            update.set("photo", new Author.Photo(f.photoUrl(), Source.WIKIDATA)).set("fieldSources.photo", Source.WIKIDATA);
            changed = true;
        }
        if (!changed) return null;
        return update.set("updatedAt", new Date());
    }

    private static boolean open(Object current, Source source) {
        return current == null || source == Source.WIKIDATA;
    }

    private AuthorImportJob.Coverage coverage() {
        long ng = mongoTemplate.count(query(where("countries").is("NG").and("status").ne(Author.Status.MERGED)), Author.class);
        long africa = mongoTemplate.count(query(where("countries").in(WikidataCountries.AFRICAN_CODES)
                .and("status").ne(Author.Status.MERGED)), Author.class);
        return new AuthorImportJob.Coverage(ng, africa);
    }
}

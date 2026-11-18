package com.personal.rebooked.author.importer;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Duration;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * The Open Library bulk dumps (https://openlibrary.org/data), never the live API. "…_latest.txt.gz" redirects to a
 * dated file (archive.org/download/ol_dump_2026-10-31/ol_dump_authors_2026-10-31.txt.gz); that date is the dataset
 * version.
 */
@Slf4j
@Component
public class OpenLibraryDumps {
    private static final Pattern DATED = Pattern.compile("ol_dump_(authors|works)_(\\d{4}-\\d{2}-\\d{2})\\.txt\\.gz$");
    private static final long HEARTBEAT_BYTES = 64L * 1024 * 1024;

    private final String baseUrl;
    private final Path dir;
    private final String userAgent;
    private final HttpClient noRedirects = HttpClient.newBuilder()
            .followRedirects(HttpClient.Redirect.NEVER).connectTimeout(Duration.ofSeconds(20)).build();
    private final HttpClient download = HttpClient.newBuilder()
            .followRedirects(HttpClient.Redirect.NORMAL).connectTimeout(Duration.ofSeconds(20)).build();

    public OpenLibraryDumps(@Value("${authors.import.openlibrary-url:https://openlibrary.org/data}") String baseUrl,
                            @Value("${authors.import.dir:./data/imports}") String dir,
                            @Value("${wikidata.user-agent:RebookedAuthorImporter/1.0}") String userAgent) {
        this.baseUrl = baseUrl.replaceAll("/+$", "");
        this.dir = Path.of(dir);
        this.userAgent = userAgent;
    }

    public record Dump(String kind, String datasetVersion, URI url, String fileName) {}

    public record Latest(String datasetVersion, Dump authors, Dump works) {}

    public Latest resolveLatest() throws IOException, InterruptedException {
        Dump authors = resolve("authors");
        Dump works = resolve("works");
        if (!authors.datasetVersion().equals(works.datasetVersion())) {
            log.warn("Open Library authors dump is {} but works dump is {}", authors.datasetVersion(), works.datasetVersion());
        }
        return new Latest(authors.datasetVersion(), authors, works);
    }

    private Dump resolve(String kind) throws IOException, InterruptedException {
        URI uri = URI.create(baseUrl + "/ol_dump_" + kind + "_latest.txt.gz");
        for (int hop = 0; hop < 6; hop++) {
            Matcher dated = DATED.matcher(uri.getPath());
            if (dated.find()) return new Dump(kind, dated.group(2), uri, dated.group(0));
            HttpResponse<Void> response = noRedirects.send(HttpRequest.newBuilder(uri)
                    .method("HEAD", HttpRequest.BodyPublishers.noBody())
                    .header("User-Agent", userAgent)
                    .timeout(Duration.ofSeconds(30)).build(), HttpResponse.BodyHandlers.discarding());
            if (response.statusCode() / 100 != 3) {
                throw new IOException("Expected a redirect to a dated dump from " + uri + ", got HTTP " + response.statusCode());
            }
            URI from = uri;
            String location = response.headers().firstValue("location")
                    .orElseThrow(() -> new IOException("Redirect without a Location header from " + from));
            uri = from.resolve(location);
        }
        throw new IOException("Too many redirects resolving the latest " + kind + " dump");
    }

    public Path pathFor(Dump dump) {
        return dir.resolve(dump.fileName());
    }

    /** Streams the dump to disk (a .part file, moved into place when complete). Skips a file that's already there. */
    public Path download(Dump dump, Runnable heartbeat) throws IOException, InterruptedException {
        Path target = pathFor(dump);
        if (Files.exists(target) && Files.size(target) > 0) {
            log.info("Using the {} dump already downloaded at {}", dump.kind(), target);
            return target;
        }
        Files.createDirectories(dir);
        Path part = dir.resolve(dump.fileName() + ".part");
        log.info("Downloading {} to {}", dump.url(), target);
        HttpResponse<InputStream> response = download.send(HttpRequest.newBuilder(dump.url())
                .header("User-Agent", userAgent).GET().build(), HttpResponse.BodyHandlers.ofInputStream());
        if (response.statusCode() != 200) {
            response.body().close();
            throw new IOException("Downloading " + dump.url() + " failed with HTTP " + response.statusCode());
        }
        try (InputStream in = response.body(); OutputStream out = Files.newOutputStream(part)) {
            byte[] buffer = new byte[1 << 20];
            long sinceHeartbeat = 0;
            int n;
            while ((n = in.read(buffer)) >= 0) {
                out.write(buffer, 0, n);
                sinceHeartbeat += n;
                if (sinceHeartbeat >= HEARTBEAT_BYTES) {
                    heartbeat.run();
                    sinceHeartbeat = 0;
                }
            }
        }
        Files.move(part, target, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        return target;
    }

    public void delete(Latest latest) {
        for (Dump dump : new Dump[]{latest.authors(), latest.works()}) {
            try {
                Files.deleteIfExists(pathFor(dump));
            } catch (IOException e) {
                log.warn("Couldn't delete {}: {}", pathFor(dump), e.toString());
            }
        }
    }
}

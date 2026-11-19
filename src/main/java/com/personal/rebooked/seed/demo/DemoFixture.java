package com.personal.rebooked.seed.demo;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import java.util.zip.GZIPInputStream;

/**
 * The real books the demo seeder lists: seed/openlibrary-books.json.gz, built by scripts/seed/fetch_openlibrary.py
 * from the Open Library search API. Categories are names from Constants.categoryList, the queried one first.
 */
public final class DemoFixture {
    private static final ObjectMapper JSON = new ObjectMapper()
            .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);

    public record Work(String workKey, String title, List<WorkAuthor> authors, long coverId, String isbn,
                       Integer year, Integer pages, List<String> categories, List<String> subjects) {
        public String coverUrl() {
            return "https://covers.openlibrary.org/b/id/" + coverId + "-L.jpg";
        }

        public String primaryCategory() {
            return categories == null || categories.isEmpty() ? null : categories.get(0);
        }

        /** Nigerian and African literature gets a popularity boost: this is a Nigerian marketplace. */
        public boolean african() {
            return subjects != null && subjects.stream().map(String::toLowerCase)
                    .anyMatch(s -> s.contains("nigeria") || s.contains("africa"));
        }
    }

    public record WorkAuthor(String name, String olKey) {}

    private DemoFixture() {}

    public static List<Work> read(Path path) throws IOException {
        if (!Files.exists(path)) {
            throw new IOException("Demo fixture " + path.toAbsolutePath() + " not found. Run from the project root, "
                    + "set seed.demo.fixture, or rebuild it with python3 scripts/seed/fetch_openlibrary.py");
        }
        try (InputStream in = new GZIPInputStream(Files.newInputStream(path))) {
            return read(in);
        }
    }

    static List<Work> read(InputStream json) throws IOException {
        Work[] works = JSON.readValue(json, Work[].class);
        return Arrays.stream(works)
                .filter(w -> w.title() != null && !w.title().isBlank() && w.coverId() > 0
                        && w.authors() != null && !w.authors().isEmpty())
                .toList();
    }
}

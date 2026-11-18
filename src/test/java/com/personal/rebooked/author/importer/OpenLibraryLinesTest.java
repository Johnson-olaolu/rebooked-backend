package com.personal.rebooked.author.importer;

import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class OpenLibraryLinesTest {
    private final ObjectMapper mapper = new ObjectMapper();

    @Test
    void splitsTheFiveColumns() {
        OpenLibraryLines.Line line = OpenLibraryLines.split(
                "/type/author\t/authors/OL23919A\t12\t2026-01-01T00:00:00\t{\"name\": \"x\\ty\"}");
        assertThat(line.type()).isEqualTo("/type/author");
        assertThat(line.key()).isEqualTo("/authors/OL23919A");
        assertThat(line.json()).isEqualTo("{\"name\": \"x\\ty\"}");
        assertThat(OpenLibraryLines.split("/type/author\tonly two")).isNull();
    }

    @Test
    void authorKeys() {
        assertThat(OpenLibraryLines.olNumber("/authors/OL23919A")).isEqualTo(23919);
        assertThat(OpenLibraryLines.olNumber("/works/OL1W")).isEqualTo(-1);
        assertThat(OpenLibraryLines.olId("/authors/OL23919A")).isEqualTo("OL23919A");
    }

    @Test
    void readsWorkAuthorsInEveryShape() throws Exception {
        String json = """
                {"title": "Good Omens", "subjects": ["x", {"nested": [1, 2]}],
                 "authors": [
                   {"type": {"key": "/type/author_role"}, "author": {"key": "/authors/OL1A"}},
                   {"author": "/authors/OL2A"},
                   {"key": "/authors/OL3A"},
                   "junk",
                   {"author": {"key": "/authors/OL1A", "extra": {"deep": true}}}
                 ],
                 "key": "/works/OL9W"}
                """;
        List<String> keys = new ArrayList<>();
        OpenLibraryLines.workAuthorKeys(new JsonFactory(), json, keys::add);
        assertThat(keys).containsExactly("/authors/OL1A", "/authors/OL2A", "/authors/OL3A", "/authors/OL1A");
    }

    @Test
    void mapsAnAuthorRecord() throws Exception {
        String json = """
                {"name": "Chinua  Achebe", "personal_name": "Chinua Achebe",
                 "alternate_names": ["Albert Chinualumogu Achebe", "CHINUA ACHEBE", "A"],
                 "fuller_name": "Albert Chinụalụmọgụ Achebe",
                 "bio": {"type": "/type/text", "value": "<p>Nigerian <b>novelist</b>.</p>"},
                 "birth_date": "16 November 1930", "death_date": "c. 2013",
                 "photos": [-1, 6257776],
                 "remote_ids": {"wikidata": "Q155845", "viaf": "17222771", "isni": "0000 0001 2140 6339"}}
                """;
        OpenLibraryLines.AuthorRecord r = OpenLibraryLines.author(mapper, "/authors/OL23919A", json);
        assertThat(r.olId()).isEqualTo("OL23919A");
        assertThat(r.name()).isEqualTo("Chinua Achebe");
        assertThat(r.aliases()).containsExactly("Albert Chinualumogu Achebe", "Albert Chinụalụmọgụ Achebe");
        assertThat(r.biography()).isEqualTo("Nigerian novelist.");
        assertThat(r.birthYear()).isEqualTo(1930);
        assertThat(r.deathYear()).isEqualTo(2013);
        assertThat(r.photoUrl()).isEqualTo("https://covers.openlibrary.org/a/id/6257776-M.jpg");
        assertThat(r.wikidata()).isEqualTo("Q155845");
        assertThat(r.viaf()).isEqualTo("17222771");
        assertThat(r.isni()).isEqualTo("0000000121406339");
        assertThat(r.hasStrongId()).isTrue();
    }

    @Test
    void bioCanBeAPlainStringAndIdsCanBeMissingOrInvalid() throws Exception {
        OpenLibraryLines.AuthorRecord r = OpenLibraryLines.author(mapper, "/authors/OL1A",
                "{\"name\": \"Someone\", \"bio\": \"Wrote things.\", \"remote_ids\": {\"wikidata\": \"not-a-qid\"}}");
        assertThat(r.biography()).isEqualTo("Wrote things.");
        assertThat(r.wikidata()).isNull();
        assertThat(r.hasStrongId()).isFalse();
        assertThat(r.birthYear()).isNull();
    }
}

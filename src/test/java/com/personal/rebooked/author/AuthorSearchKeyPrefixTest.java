package com.personal.rebooked.author;

import com.personal.rebooked.author.models.Author;
import org.bson.Document;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/** §23j: one searchKey must satisfy both bounds of the prefix range. */
class AuthorSearchKeyPrefixTest {

    /** Keys of "E2E Ada Testwriter", which straddle "someone": one key is >= it, another < "someone￿". */
    private static final List<String> STRADDLING_KEYS = AuthorNames.searchKeys("E2E Ada Testwriter", List.of());

    @Test
    void criteriaUsesElemMatchSoOneKeyMeetsBothBounds() {
        Document criteria = AuthorSearch.keyPrefix("someone").getCriteriaObject();

        Document expected = new Document("searchKeys",
                new Document("$elemMatch", new Document("$gte", "someone").append("$lt", "someone￿")));
        assertThat(criteria).isEqualTo(expected);
    }

    @Test
    void straddlingKeysMatchSeparateBoundsButNoSingleKey() {
        String q = "someone";
        assertThat(STRADDLING_KEYS).containsExactlyInAnyOrder("e2e ada testwriter", "ada testwriter", "testwriter");

        // What the old plain range did on an array: each bound may be met by a different key
        boolean anyGte = STRADDLING_KEYS.stream().anyMatch(k -> k.compareTo(q) >= 0);
        boolean anyLt = STRADDLING_KEYS.stream().anyMatch(k -> k.compareTo(q + "￿") < 0);
        assertThat(anyGte && anyLt).isTrue();

        // What $elemMatch does: the same key must meet both
        assertThat(STRADDLING_KEYS.stream().anyMatch(k -> inPrefixRange(k, q))).isFalse();
    }

    @Test
    void aRealWordStartStillMatches() {
        String q = AuthorNames.fold("Testwriter");
        assertThat(STRADDLING_KEYS.stream().anyMatch(k -> inPrefixRange(k, q))).isTrue();

        Author author = new Author();
        author.setId("1");
        author.setName("E2E Ada Testwriter");
        author.setStatus(Author.Status.UNVERIFIED);
        assertThat(AuthorSearch.classify(author, q).tier()).isEqualTo(2);
    }

    /** Mirrors the bounds inside keyPrefix's $elemMatch. */
    private static boolean inPrefixRange(String key, String q) {
        return key.compareTo(q) >= 0 && key.compareTo(q + "￿") < 0;
    }
}

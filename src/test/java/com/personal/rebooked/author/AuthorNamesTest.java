package com.personal.rebooked.author;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class AuthorNamesTest {

    @Test
    void normalizeTrimsCollapsesAndLowercasesButKeepsPunctuation() {
        assertThat(AuthorNames.normalize("CHINUA   Achebe")).isEqualTo("chinua achebe");
        assertThat(AuthorNames.normalize("  Ngũgĩ wa Thiong'o ")).isEqualTo("ngũgĩ wa thiong'o");
        assertThat(AuthorNames.normalize("J.R.R. Tolkien")).isEqualTo("j.r.r. tolkien");
        assertThat(AuthorNames.normalize(null)).isEmpty();
    }

    @Test
    void foldStripsAccentsAndTurnsPunctuationIntoSpaces() {
        assertThat(AuthorNames.fold("Ngũgĩ wa Thiong'o")).isEqualTo("ngugi wa thiong o");
        assertThat(AuthorNames.fold("Chimamanda Ngozi ADICHIE")).isEqualTo("chimamanda ngozi adichie");
        assertThat(AuthorNames.fold("J.R.R. Tolkien")).isEqualTo("j r r tolkien");
        assertThat(AuthorNames.fold("Søren Kierkegaard")).isEqualTo("soren kierkegaard");
        assertThat(AuthorNames.fold("Stanisław Lem")).isEqualTo("stanislaw lem");
        assertThat(AuthorNames.fold("!!!")).isEmpty();
    }

    @Test
    void foldKeepsNonLatinLetters() {
        assertThat(AuthorNames.fold("Лев Толстой")).isEqualTo("лев толстой");
        assertThat(AuthorNames.fold("村上 春樹")).isEqualTo("村上 春樹");
        assertThat(AuthorNames.fold("प्रेमचंद")).isEqualTo("प्रेमचंद");
        assertThat(AuthorNames.fold("Ngũgĩ Лев")).isEqualTo("ngugi лев");
    }

    @Test
    void searchKeysAreWordSuffixesOfNameThenAliases() {
        assertThat(AuthorNames.searchKeys("Chinua Achebe", List.of("Albert Chinualumogu Achebe")))
                .containsExactly("chinua achebe", "achebe",
                        "albert chinualumogu achebe", "chinualumogu achebe");
    }

    @Test
    void searchKeysAreCappedAt40() {
        List<String> aliases = new ArrayList<>();
        for (int i = 0; i < 30; i++) aliases.add("Alias Number" + i + " Long Name");
        assertThat(AuthorNames.searchKeys("Some Author", aliases)).hasSize(AuthorNames.MAX_SEARCH_KEYS);
    }

    @Test
    void laterWordStartsWith() {
        assertThat(AuthorNames.laterWordStartsWith("chinua achebe", "ach")).isTrue();
        assertThat(AuthorNames.laterWordStartsWith("chinua achebe", "chi")).isFalse();
        assertThat(AuthorNames.laterWordStartsWith("chinua achebe", "hebe")).isFalse();
    }
}

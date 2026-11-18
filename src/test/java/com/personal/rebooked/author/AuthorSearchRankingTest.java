package com.personal.rebooked.author;

import com.personal.rebooked.author.models.Author;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class AuthorSearchRankingTest {

    private static Author author(String id, String name, long available, Author.Status status, int works, String... aliases) {
        Author a = new Author();
        a.setId(id);
        a.setName(name);
        a.setStatus(status);
        a.setAvailableCount(available);
        a.setWorkCount(works);
        for (String alias : aliases) a.getAliases().add(new Author.Alias(alias, AuthorNames.normalize(alias), Author.Source.OPEN_LIBRARY));
        return a;
    }

    @Test
    void tiersComeFirstThenCountsVerifiedWorksAndName() {
        Author exact = author("1", "Achebe", 0, Author.Status.UNVERIFIED, 0);
        Author prefixBusy = author("2", "Achebe Chinwe", 5, Author.Status.UNVERIFIED, 0);
        Author prefixQuiet = author("3", "Achebe Nwando", 0, Author.Status.VERIFIED, 10);
        Author laterWord = author("4", "Chinua Achebe", 12, Author.Status.VERIFIED, 127);

        List<AuthorSearch.Hit> hits = AuthorSearch.rank(List.of(laterWord, prefixQuiet, exact, prefixBusy), "achebe");

        assertThat(hits).extracting(h -> h.author().getId()).containsExactly("1", "2", "3", "4");
        assertThat(hits).extracting(AuthorSearch.Hit::tier).containsExactly(0, 1, 1, 2);
    }

    @Test
    void verifiedThenWorkCountBreakTiesWithinATier() {
        Author unverified = author("1", "Ama Ata", 0, Author.Status.UNVERIFIED, 50);
        Author verifiedFew = author("2", "Ama Aidoo", 0, Author.Status.VERIFIED, 3);
        Author verifiedMany = author("3", "Ama Darko", 0, Author.Status.VERIFIED, 40);

        List<AuthorSearch.Hit> hits = AuthorSearch.rank(List.of(unverified, verifiedFew, verifiedMany), "ama");

        assertThat(hits).extracting(h -> h.author().getId()).containsExactly("3", "2", "1");
    }

    @Test
    void aliasMatchesCarryTheAlias() {
        Author achebe = author("1", "Chinua Achebe", 0, Author.Status.VERIFIED, 0, "Albert Chinualumogu Achebe");

        AuthorSearch.Hit byAlias = AuthorSearch.classify(achebe, "albert chinu");
        assertThat(byAlias.tier()).isEqualTo(2);
        assertThat(byAlias.matchedAlias()).isEqualTo("Albert Chinualumogu Achebe");

        AuthorSearch.Hit exactAlias = AuthorSearch.classify(achebe, "albert chinualumogu achebe");
        assertThat(exactAlias.tier()).isZero();
        assertThat(exactAlias.matchedAlias()).isEqualTo("Albert Chinualumogu Achebe");

        AuthorSearch.Hit byName = AuthorSearch.classify(achebe, "chinua");
        assertThat(byName.tier()).isEqualTo(1);
        assertThat(byName.matchedAlias()).isNull();
    }

    @Test
    void accentsDontMatter() {
        Author ngugi = author("1", "Ngũgĩ wa Thiong'o", 0, Author.Status.VERIFIED, 0);
        assertThat(AuthorSearch.classify(ngugi, AuthorNames.fold("ngugi")).tier()).isEqualTo(1);
        assertThat(AuthorSearch.classify(ngugi, AuthorNames.fold("Thiong")).tier()).isEqualTo(2);
    }
}

package com.personal.rebooked.author.importer;

import com.personal.rebooked.author.AuthorNames;
import com.personal.rebooked.author.models.Author;
import com.personal.rebooked.author.models.Author.Source;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class MergeAliasesTest {

    private static Author.Alias alias(String name, Source source) {
        return new Author.Alias(name, AuthorNames.normalize(name), source);
    }

    @Test
    void replacesOnlyTheSourcesOwnAliases() {
        List<Author.Alias> current = List.of(alias("Old OL Name", Source.OPEN_LIBRARY), alias("Admin Alias", Source.ADMIN));

        List<Author.Alias> merged = OpenLibraryImporter.mergeAliases(current, List.of("New OL Name"), "Author", Source.OPEN_LIBRARY);

        assertThat(merged).containsExactly(alias("Admin Alias", Source.ADMIN), alias("New OL Name", Source.OPEN_LIBRARY));
    }

    @Test
    void skipsTheNameAndOtherSourcesDuplicates() {
        List<Author.Alias> current = List.of(alias("Shared Name", Source.ADMIN));

        List<Author.Alias> merged = OpenLibraryImporter.mergeAliases(current,
                List.of("CHINUA ACHEBE", "shared   name", "Albert Achebe", "Albert Achebe"), "Chinua Achebe", Source.WIKIDATA);

        assertThat(merged).containsExactly(alias("Shared Name", Source.ADMIN), alias("Albert Achebe", Source.WIKIDATA));
    }

    @Test
    void theLinkedOpenLibraryRecordOwnsAMergedAuthor() {
        Author merged = new Author();
        merged.setLinks(new Author.Links("https://openlibrary.org/authors/OL1A", null, null));
        assertThat(OpenLibraryImporter.primaryOlId(merged, "OL6A")).isEqualTo("OL1A");

        Author unlinked = new Author();
        assertThat(OpenLibraryImporter.primaryOlId(unlinked, "OL6A")).isEqualTo("OL6A");
    }

    @Test
    void reRunningEitherSourceIsStable() {
        List<Author.Alias> start = List.of(alias("OL Alias", Source.OPEN_LIBRARY), alias("C. Achebe", Source.ADMIN));
        List<Author.Alias> afterWikidata = OpenLibraryImporter.mergeAliases(start, List.of("WD Alias"), "Chinua Achebe", Source.WIKIDATA);
        List<Author.Alias> afterOpenLibrary = OpenLibraryImporter.mergeAliases(afterWikidata, List.of("OL Alias"), "Chinua Achebe", Source.OPEN_LIBRARY);
        List<Author.Alias> afterWikidataAgain = OpenLibraryImporter.mergeAliases(afterOpenLibrary, List.of("WD Alias"), "Chinua Achebe", Source.WIKIDATA);

        assertThat(afterOpenLibrary).isEqualTo(afterWikidata);
        assertThat(afterWikidataAgain).isEqualTo(afterWikidata);
        assertThat(afterWikidata).extracting(Author.Alias::getName).containsExactly("OL Alias", "C. Achebe", "WD Alias");
    }
}

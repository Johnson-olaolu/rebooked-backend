package com.personal.rebooked.author.migration;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class AuthorMigrationSplitTest {

    @Test
    void splitsOnlyOnAmpersandAndSemicolon() {
        assertThat(AuthorMigration.split("Terry Pratchett & Neil Gaiman").names())
                .containsExactly("Terry Pratchett", "Neil Gaiman");
        assertThat(AuthorMigration.split("A. Author; B. Author;C. Author").names())
                .containsExactly("A. Author", "B. Author", "C. Author");
    }

    @Test
    void commasAndAndAreKeptAndFlagged() {
        AuthorMigration.Split comma = AuthorMigration.split("Achebe, Chinua");
        assertThat(comma.names()).containsExactly("Achebe, Chinua");
        assertThat(comma.needsCheck()).isTrue();

        AuthorMigration.Split and = AuthorMigration.split("Brian Kernighan and Dennis Ritchie");
        assertThat(and.names()).containsExactly("Brian Kernighan and Dennis Ritchie");
        assertThat(and.needsCheck()).isTrue();

        assertThat(AuthorMigration.split("Alexander Hamilton").needsCheck()).isFalse(); // "and" only as a word
    }

    @Test
    void cleansAndDeduplicates() {
        assertThat(AuthorMigration.split("  Chinua   Achebe  & chinua achebe ;  ").names()).containsExactly("Chinua Achebe");
        assertThat(AuthorMigration.split("Tom&Jerry").names()).containsExactly("Tom&Jerry"); // no spaces: not a separator
        assertThat(AuthorMigration.split("   ").names()).isEmpty();
    }
}

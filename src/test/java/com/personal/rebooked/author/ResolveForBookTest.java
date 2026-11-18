package com.personal.rebooked.author;

import com.personal.rebooked.author.models.Author;
import com.personal.rebooked.author.repositories.AuthorRepository;
import com.personal.rebooked.book.models.BookAuthor;
import com.personal.rebooked.service.RedisService;
import org.bson.types.ObjectId;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.mongodb.core.MongoTemplate;

import java.util.*;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ResolveForBookTest {
    private final Map<String, Author> store = new HashMap<>();
    private AuthorService service;

    @BeforeEach
    void setUp() {
        AuthorRepository repository = mock(AuthorRepository.class);
        when(repository.findAllById(any())).thenAnswer(inv -> {
            List<Author> found = new ArrayList<>();
            for (Object id : (Iterable<?>) inv.getArgument(0)) if (store.containsKey(id)) found.add(store.get(id));
            return found;
        });
        when(repository.findById(anyString())).thenAnswer(inv -> Optional.ofNullable(store.get(inv.<String>getArgument(0))));
        service = new AuthorService(repository, mock(AuthorSearch.class), mock(AuthorReviewQueue.class),
                mock(MongoTemplate.class), mock(RedisService.class));
    }

    private Author add(String name) {
        Author a = new Author();
        a.setId(new ObjectId().toHexString());
        a.setName(name);
        a.setSlug(name.toLowerCase(Locale.ROOT).replace(' ', '-'));
        a.setStatus(Author.Status.VERIFIED);
        store.put(a.getId(), a);
        return a;
    }

    private Author mergedInto(Author target) {
        Author a = add("Old " + target.getName());
        a.setStatus(Author.Status.MERGED);
        a.setMergedIntoAuthorId(target.getId());
        return a;
    }

    @Test
    void keepsTheOrderSentAsPositions() {
        Author pratchett = add("Terry Pratchett");
        Author gaiman = add("Neil Gaiman");
        Map<String, String> errors = new LinkedHashMap<>();

        List<BookAuthor> authors = service.resolveForBook(List.of(gaiman.getId(), pratchett.getId()), errors);

        assertThat(errors).isEmpty();
        assertThat(authors).extracting(BookAuthor::getAuthorId).containsExactly(gaiman.getId(), pratchett.getId());
        assertThat(authors).extracting(BookAuthor::getPosition).containsExactly(0, 1);
        assertThat(authors.get(0).getName()).isEqualTo("Neil Gaiman");
        assertThat(authors.get(0).getSlug()).isEqualTo("neil-gaiman");
    }

    @Test
    void emptyListClears() {
        Map<String, String> errors = new HashMap<>();
        assertThat(service.resolveForBook(List.of(), errors)).isEmpty();
        assertThat(errors).isEmpty();
    }

    @Test
    void unknownOrInvalidIds() {
        Map<String, String> errors = new HashMap<>();
        assertThat(service.resolveForBook(List.of(new ObjectId().toHexString()), errors)).isNull();
        assertThat(errors).containsEntry("authorIds", "One of these authors no longer exists");

        errors.clear();
        assertThat(service.resolveForBook(List.of("not-an-id"), errors)).isNull();
        assertThat(errors).containsEntry("authorIds", "One of these authors no longer exists");
    }

    @Test
    void mergedIdsAreReplacedByTheirCanonicalAuthor() {
        Author canonical = add("Chinua Achebe");
        Author old = mergedInto(canonical);
        Map<String, String> errors = new HashMap<>();

        List<BookAuthor> authors = service.resolveForBook(List.of(old.getId()), errors);

        assertThat(errors).isEmpty();
        assertThat(authors).extracting(BookAuthor::getAuthorId).containsExactly(canonical.getId());
    }

    @Test
    void aChainLongerThanFiveHopsCountsAsMissing() {
        Author current = add("End");
        for (int i = 0; i < 6; i++) current = mergedInto(current);
        Map<String, String> errors = new HashMap<>();

        assertThat(service.resolveForBook(List.of(current.getId()), errors)).isNull();
        assertThat(errors).containsEntry("authorIds", "One of these authors no longer exists");
    }

    @Test
    void theSameAuthorTwiceAfterResolvingMerges() {
        Author canonical = add("Chinua Achebe");
        Author old = mergedInto(canonical);
        Map<String, String> errors = new HashMap<>();

        assertThat(service.resolveForBook(List.of(canonical.getId(), old.getId()), errors)).isNull();
        assertThat(errors).containsEntry("authorIds", "Add each author once");
    }

    @Test
    void atMostTen() {
        List<String> ids = new ArrayList<>();
        for (int i = 0; i < 11; i++) ids.add(add("Author " + i).getId());
        Map<String, String> errors = new HashMap<>();

        assertThat(service.resolveForBook(ids, errors)).isNull();
        assertThat(errors).containsEntry("authorIds", "Up to 10 authors");
    }

    @Test
    void proposalNamesAreChecked() {
        assertThat(AuthorService.validateName("Chinua Achebe")).isNull();
        assertThat(AuthorService.validateName("J.R.R. Tolkien")).isNull();
        assertThat(AuthorService.validateName("A")).isNotNull();
        assertThat(AuthorService.validateName("1234")).isEqualTo("The name must contain a letter");
        assertThat(AuthorService.validateName("https://example.com/x")).isNotNull();
        assertThat(AuthorService.validateName("www.example.org")).isNotNull();
        assertThat(AuthorService.validateName("example.com")).isNotNull();
        assertThat(AuthorService.validateName("me@example.com")).isNotNull();
        assertThat(AuthorService.validateName("x".repeat(121))).isNotNull();
    }
}

package com.personal.rebooked.homepage.sections;

import com.personal.rebooked.book.models.Book;
import com.personal.rebooked.book.models.BookAuthor;
import com.personal.rebooked.category.models.Category;
import com.personal.rebooked.user.models.User;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class ForYouSectionTest {
    private static Category category(String id, String name) {
        Category c = new Category();
        c.setId(id);
        c.setName(name);
        return c;
    }

    private static Book book(String ownerId, List<Category> categories, String... authors) {
        Book b = new Book();
        User owner = new User();
        owner.setId(ownerId);
        b.setUser(owner);
        b.setCategories(categories);
        List<BookAuthor> list = new ArrayList<>();
        for (String a : authors) list.add(new BookAuthor(a, list.size(), a.toUpperCase(), a));
        b.setAuthors(list);
        return b;
    }

    @Test
    void topThreeCategoriesAndAuthorsFromHistory() {
        Category mystery = category("c1", "Mystery"), romance = category("c2", "Romance"),
                poetry = category("c3", "Poetry"), history = category("c4", "History");
        List<Book> books = List.of(
                book("seller", List.of(mystery), "achebe"),
                book("seller", List.of(mystery, romance), "achebe"),
                book("seller", List.of(mystery, poetry), "adichie"),
                book("seller", List.of(romance), "soyinka"),
                book("seller", List.of(history), "emecheta"),
                book("seller", List.of(poetry), "adichie"));

        ForYouSection.Taste taste = ForYouSection.taste(books, "reader");

        assertThat(taste.categories().values()).containsExactly("Mystery", "Romance", "Poetry"); // Romance and Poetry tie at 2: by id
        assertThat(taste.authors().values()).containsExactly("ACHEBE", "ADICHIE", "EMECHETA");
        assertThat(taste.names()).hasSize(6);
    }

    @Test
    void theReadersOwnBooksDontCount() {
        ForYouSection.Taste taste = ForYouSection.taste(List.of(book("reader", List.of(category("c1", "Mystery")), "achebe")), "reader");
        assertThat(taste.isEmpty()).isTrue();
    }
}

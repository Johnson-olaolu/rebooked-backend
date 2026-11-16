package com.personal.rebooked.book.saved;

import com.personal.rebooked.book.models.Book;

import java.util.List;

/** GET /book/saved */
public record SavedBooksPage(List<Book> content, int page, int pageSize, long totalElements, int totalPages) {
}

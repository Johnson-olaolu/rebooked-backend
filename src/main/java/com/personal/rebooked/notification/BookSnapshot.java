package com.personal.rebooked.notification;

import com.personal.rebooked.book.models.Book;

/** The book as it was when notified, so the notification still renders after the book is removed. */
public record BookSnapshot(String id, String title, String coverUrl, String slug) {
    public static BookSnapshot of(Book book) {
        if (book == null) return null;
        return new BookSnapshot(book.getId(), book.getTitle(),
                book.getCoverImage() != null ? book.getCoverImage().getUrl() : null, book.getSlug());
    }
}

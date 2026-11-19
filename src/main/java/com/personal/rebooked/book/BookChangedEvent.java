package com.personal.rebooked.book;

import com.personal.rebooked.book.models.Book;
import com.personal.rebooked.utils.Constants;

/**
 * Published by BookService after a seller creates, edits, changes the status of, or deletes a book,
 * so conversations holding it (and the homepage snapshots) can follow. Chat's own "sold to this buyer"
 * doesn't go through here. previousStatus is null for CREATED.
 */
public record BookChangedEvent(Book book, Kind kind, Constants.BookStatus previousStatus) {
    public enum Kind { CREATED, UPDATED, STATUS_CHANGED, DELETED }
}

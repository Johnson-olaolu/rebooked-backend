package com.personal.rebooked.homepage.dto;

import com.personal.rebooked.book.models.Book;

/** An AI pick in responses: the live book and why it was picked. */
public record AiPick(Book book, String reason) {
}

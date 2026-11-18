package com.personal.rebooked.author.admin.dto;

import com.personal.rebooked.book.models.Book;

import java.util.List;

/** from and into in the admin detail shape with bookCount; sharedBooks list both authors. */
public record MergePreview(AdminAuthorDetail from, AdminAuthorDetail into, List<Book> sharedBooks) {}

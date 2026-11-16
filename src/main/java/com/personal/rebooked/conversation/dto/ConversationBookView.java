package com.personal.rebooked.conversation.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.personal.rebooked.utils.Constants;

import java.util.Date;

/** price, isFree, condition and status are live; all four are null once the book is deleted. */
public record ConversationBookView(String bookId, String title, String coverUrl, Double price,
                                   @JsonProperty("isFree") Boolean isFree, Constants.BookCondition condition,
                                   Constants.BookStatus status, Constants.ConversationBookState state,
                                   String role, Date addedAt, Date lastMessageAt) {}

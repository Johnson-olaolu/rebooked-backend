package com.personal.rebooked.chat.dto;

import jakarta.validation.constraints.NotBlank;

public record CreateChatDTO (
        @NotBlank String sellerId,
        @NotBlank  String buyerId,
        @NotBlank  String bookId,
        Boolean signUpForNewsletter
){
    public CreateChatDTO(String sellerId, String buyerId, String bookId) {
        this(sellerId, buyerId, bookId, false);
    }
}

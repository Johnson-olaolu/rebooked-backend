package com.personal.rebooked.homepage.dto;

/** One AI pick as stored in Redis: the book id and the model's one-sentence reason. */
public record PickRef(String id, String reason) {
}

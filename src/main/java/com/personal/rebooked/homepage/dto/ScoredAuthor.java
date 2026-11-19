package com.personal.rebooked.homepage.dto;

/** A top author of the week as stored in Redis. */
public record ScoredAuthor(String authorId, long weekScore) {
}

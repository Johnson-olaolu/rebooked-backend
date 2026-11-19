package com.personal.rebooked.homepage.dto;

import com.fasterxml.jackson.annotation.JsonUnwrapped;
import com.personal.rebooked.author.dto.AuthorSummary;

/** topAuthors[]: an AuthorSummary plus photoUrl (after the licensing gate) and the week's score. */
public record TopAuthorView(@JsonUnwrapped AuthorSummary author, String photoUrl, long weekScore) {
}

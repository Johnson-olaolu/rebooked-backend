package com.personal.rebooked.author.dto;

import java.util.List;

/** Either the new author (201) or the close matches to choose from (200, author null). */
public record ProposalResult(AuthorSummary author, List<AuthorSummary> candidates) {}

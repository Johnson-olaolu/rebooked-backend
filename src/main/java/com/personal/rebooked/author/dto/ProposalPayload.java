package com.personal.rebooked.author.dto;

/** POST /author/proposals. force adds the author even when close matches exist. Validated in AuthorService. */
public record ProposalPayload(String name, Boolean force) {}

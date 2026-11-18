package com.personal.rebooked.author.admin.dto;

import java.util.List;
import java.util.Map;

/** counts: authors per status for the same q and origin, ignoring the status filter. */
public record AdminAuthorPage(List<AdminAuthorRow> content, int page, int pageSize, long totalElements, int totalPages,
                              Map<String, Long> counts) {}

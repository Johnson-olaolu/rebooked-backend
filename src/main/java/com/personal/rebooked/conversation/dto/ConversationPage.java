package com.personal.rebooked.conversation.dto;

import java.util.List;
import java.util.Map;

/** counts ignore filter and search. */
public record ConversationPage(List<ConversationView> content, int page, int pageSize, long totalElements,
                               int totalPages, Map<String, Long> counts) {}

package com.personal.rebooked.conversation.dto;

import com.personal.rebooked.conversation.models.Message;

import java.util.List;

/** Newest first. */
public record MessagePage(List<Message> content, boolean hasMore) {}

package com.personal.rebooked.author.admin.dto;

import java.util.List;

/**
 * PATCH /admin/author/{id}. null = keep. "" clears biography or description. aliases replaces the whole list.
 * Every changed field becomes admin-owned (fieldSources = ADMIN), so imports never overwrite it.
 */
public record AuthorPatch(String name, List<String> aliases, String biography, String description,
                          Integer birthYear, Integer deathYear) {}

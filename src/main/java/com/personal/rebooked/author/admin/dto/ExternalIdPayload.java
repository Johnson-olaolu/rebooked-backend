package com.personal.rebooked.author.admin.dto;

/** source: OPEN_LIBRARY, WIKIDATA, VIAF or ISNI. */
public record ExternalIdPayload(String source, String sourceId) {}

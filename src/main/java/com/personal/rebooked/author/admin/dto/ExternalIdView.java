package com.personal.rebooked.author.admin.dto;

import com.personal.rebooked.author.models.AuthorExternalIdentifier;

public record ExternalIdView(AuthorExternalIdentifier.IdSource source, String sourceId, String url) {

    public static ExternalIdView of(AuthorExternalIdentifier id) {
        return new ExternalIdView(id.getSource(), id.getSourceId(), id.getSource().url(id.getSourceId()));
    }
}

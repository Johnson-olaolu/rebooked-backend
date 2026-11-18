package com.personal.rebooked.author.admin.dto;

import com.personal.rebooked.author.models.AuthorAudit;
import com.personal.rebooked.user.dto.PublicUserDTO;

import java.util.Date;
import java.util.Map;

/** actor is null for the import ("system"). */
public record AuditEntryView(String id, AuthorAudit.Action action, Map<String, Object> before, Map<String, Object> after,
                             String reason, Date createdAt, String actorId, PublicUserDTO actor) {}

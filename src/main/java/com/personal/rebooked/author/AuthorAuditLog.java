package com.personal.rebooked.author;

import com.personal.rebooked.author.models.AuthorAudit;
import com.personal.rebooked.author.repositories.AuthorAuditRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Date;
import java.util.Map;

/** Writes author_audit entries; before/after hold only the fields that changed. */
@Slf4j
@Component
@RequiredArgsConstructor
public class AuthorAuditLog {
    public static final String SYSTEM = "system";

    private final AuthorAuditRepository audits;

    public void record(String authorId, String actorId, AuthorAudit.Action action,
                       Map<String, Object> before, Map<String, Object> after, String reason) {
        AuthorAudit audit = new AuthorAudit();
        audit.setAuthorId(authorId);
        audit.setActorId(actorId == null ? SYSTEM : actorId);
        audit.setAction(action);
        audit.setBefore(before == null || before.isEmpty() ? null : before);
        audit.setAfter(after == null || after.isEmpty() ? null : after);
        audit.setReason(reason);
        audit.setCreatedAt(new Date());
        audits.insert(audit);
        log.info("Author {} {} by {}{}", authorId, action, audit.getActorId(), reason == null ? "" : " (" + reason + ")");
    }
}

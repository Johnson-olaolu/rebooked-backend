package com.personal.rebooked.author.repositories;

import com.personal.rebooked.author.models.AuthorAudit;
import org.springframework.data.mongodb.repository.MongoRepository;

public interface AuthorAuditRepository extends MongoRepository<AuthorAudit, String> {
}

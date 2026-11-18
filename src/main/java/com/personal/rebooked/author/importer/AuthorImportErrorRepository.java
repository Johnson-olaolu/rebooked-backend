package com.personal.rebooked.author.importer;

import org.springframework.data.mongodb.repository.MongoRepository;

public interface AuthorImportErrorRepository extends MongoRepository<AuthorImportError, String> {
}

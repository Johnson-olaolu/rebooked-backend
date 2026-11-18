package com.personal.rebooked.author.repositories;

import com.personal.rebooked.author.models.AuthorExternalIdentifier;
import com.personal.rebooked.author.models.AuthorExternalIdentifier.IdSource;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface AuthorExternalIdentifierRepository extends MongoRepository<AuthorExternalIdentifier, String> {
    Optional<AuthorExternalIdentifier> findBySourceAndSourceId(IdSource source, String sourceId);

    List<AuthorExternalIdentifier> findByAuthorId(String authorId);

    List<AuthorExternalIdentifier> findByAuthorIdIn(Collection<String> authorIds);

    List<AuthorExternalIdentifier> findBySourceAndSourceIdIn(IdSource source, Collection<String> sourceIds);

    void deleteByAuthorId(String authorId);
}

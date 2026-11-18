package com.personal.rebooked.author.importer;

import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface AuthorImportJobRepository extends MongoRepository<AuthorImportJob, String> {
    boolean existsByStatusIn(Collection<AuthorImportJob.Status> statuses);

    boolean existsBySourceAndDatasetVersionAndStatus(AuthorImportJob.ImportSource source, String datasetVersion,
                                                     AuthorImportJob.Status status);

    Optional<AuthorImportJob> findFirstBySourceAndDatasetVersionAndStatusOrderByCreatedAtDesc(
            AuthorImportJob.ImportSource source, String datasetVersion, AuthorImportJob.Status status);

    Optional<AuthorImportJob> findFirstBySourceAndIdNotOrderByCreatedAtDesc(AuthorImportJob.ImportSource source, String id);

    List<AuthorImportJob> findByStatusIn(Collection<AuthorImportJob.Status> statuses);
}

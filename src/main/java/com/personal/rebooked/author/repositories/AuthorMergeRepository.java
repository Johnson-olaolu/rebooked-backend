package com.personal.rebooked.author.repositories;

import com.personal.rebooked.author.models.AuthorMerge;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.List;

public interface AuthorMergeRepository extends MongoRepository<AuthorMerge, String> {
    List<AuthorMerge> findByStateNot(AuthorMerge.State state);
}

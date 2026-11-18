package com.personal.rebooked.author.repositories;

import com.personal.rebooked.author.models.Author;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.Optional;

public interface AuthorRepository extends MongoRepository<Author, String> {
    Optional<Author> findBySlug(String slug);
}

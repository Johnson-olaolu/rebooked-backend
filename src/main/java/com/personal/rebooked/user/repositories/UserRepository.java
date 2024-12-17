package com.personal.rebooked.user.repositories;

import com.personal.rebooked.user.models.User;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.data.mongodb.repository.Query;

import java.util.List;
import java.util.Optional;

public interface UserRepository extends MongoRepository<User, String> {
    @Query("{ 'email': ?0, 'isDeleted': false }")
    Optional<User> findByEmail(String email);

    @Override
    @Query("{ '_id': ?0, 'isDeleted': false }")
    Optional<User> findById(String id);

    @Override
    @Query("{'isDeleted': false }")
    List<User> findAll();
}

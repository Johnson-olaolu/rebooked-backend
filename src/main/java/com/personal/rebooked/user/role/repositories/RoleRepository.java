package com.personal.rebooked.user.role.repositories;

import com.personal.rebooked.user.role.models.Role;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.Optional;

public interface RoleRepository extends MongoRepository<Role, String> {
    public Optional<Role> findByName(String name);
}

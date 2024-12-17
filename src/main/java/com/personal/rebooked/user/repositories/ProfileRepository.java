package com.personal.rebooked.user.repositories;

import com.personal.rebooked.user.models.Profile;
import org.springframework.data.mongodb.repository.MongoRepository;

public interface ProfileRepository extends MongoRepository<Profile, String> {
}

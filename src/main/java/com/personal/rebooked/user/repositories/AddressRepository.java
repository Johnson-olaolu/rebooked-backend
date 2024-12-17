package com.personal.rebooked.user.repositories;

import com.personal.rebooked.user.models.Address;
import org.springframework.data.mongodb.repository.MongoRepository;

public interface AddressRepository extends MongoRepository<Address, String> {
}

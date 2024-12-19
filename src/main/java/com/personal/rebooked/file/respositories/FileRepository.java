package com.personal.rebooked.file.respositories;

import com.personal.rebooked.file.models.File;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.data.mongodb.repository.Query;

import java.util.List;

public interface FileRepository extends MongoRepository<File, String> {

    @Query("{ 'user.$id': ?0 }")
    List<File> findFilesByUserId(String userId);
}

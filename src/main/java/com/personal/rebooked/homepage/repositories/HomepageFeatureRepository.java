package com.personal.rebooked.homepage.repositories;

import com.personal.rebooked.homepage.models.HomepageFeature;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.data.mongodb.repository.Query;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface HomepageFeatureRepository extends MongoRepository<HomepageFeature, String> {
    Optional<HomepageFeature> findByWeekStart(LocalDate weekStart);

    /** Weeks in [from, to), newest first. A derived query can't put two bounds on one field, hence @Query. */
    @Query(value = "{ 'weekStart': { $gte: ?0, $lt: ?1 } }", sort = "{ 'weekStart': -1 }")
    List<HomepageFeature> findWeeksBetween(LocalDate from, LocalDate to);

    List<HomepageFeature> findTop12ByOrderByWeekStartDesc();
}

package com.fitness.aiservice.repository;

import com.fitness.aiservice.model.Recommendation;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.Optional;

public interface RecommendationRepository extends MongoRepository<Recommendation, String> {
    Optional<Recommendation> findByActivityId(String activityId);
}

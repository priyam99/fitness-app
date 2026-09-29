package com.fitness.aiservice.service;

import com.fitness.aiservice.event.ActivityEvent;
import com.fitness.aiservice.model.Recommendation;
import com.fitness.aiservice.repository.RecommendationRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;

@Slf4j
@Service
@RequiredArgsConstructor
public class RecommendationService {

    private final GeminiService geminiService;
    private final RecommendationRepository recommendationRepository;

    public void generateRecommendation(ActivityEvent event) {
        log.info("Requesting Gemini recommendation for activity {}", event.getActivityId());
        String recommendationText = geminiService.getRecommendation(event);
        log.info("Gemini recommendation received: {}", recommendationText);

        Recommendation recommendation = new Recommendation();
        recommendation.setActivityId(event.getActivityId());
        recommendation.setUserId(event.getUserId());
        recommendation.setRecommendation(recommendationText);
        recommendation.setCreatedAt(LocalDateTime.now());

        recommendationRepository.save(recommendation);
        log.info("Saved recommendation for activity {}", event.getActivityId());
    }
}

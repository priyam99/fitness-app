package com.fitness.aiservice.event;

import com.fitness.aiservice.service.RecommendationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class ActivityEventConsumer {

    private final RecommendationService recommendationService;

    @KafkaListener(topics = "${kafka.topic.activity-events}", groupId = "${spring.kafka.consumer.group-id}")
    public void consume(ActivityEvent event) {
        log.info("Received activity event: {}", event);
        recommendationService.generateRecommendation(event);
    }
}

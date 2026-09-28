package com.fitness.aiservice.event;

import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class ActivityEventConsumer {

    @KafkaListener(topics = "${kafka.topic.activity-events}", groupId = "${spring.kafka.consumer.group-id}")
    public void consume(ActivityEvent event) {
        log.info("Received activity event: {}", event);
    }
}

package com.fitness.activityservice.event;

import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class ActivityEventProducer {

    private final KafkaTemplate<String, ActivityEvent> kafkaTemplate;

    @Value("${kafka.topic.activity-events}")
    private String topicName;

    public void publish(ActivityEvent event) {
        kafkaTemplate.send(topicName, event.getActivityId(), event);
    }
}

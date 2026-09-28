package com.fitness.aiservice.event;

import lombok.Data;

@Data
public class ActivityEvent {
    private String activityId;
    private String userId;
    private String type;
    private Integer durationInMinutes;
    private Integer caloriesBurned;
}

package com.fitness.activityservice.dto;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class ActivityResponse {
    private String id;
    private String userId;
    private String type;
    private Integer durationInMinutes;
    private Integer caloriesBurned;
    private LocalDateTime createdAt;
}

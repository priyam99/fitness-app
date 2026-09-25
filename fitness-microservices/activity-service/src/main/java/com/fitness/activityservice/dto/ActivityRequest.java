package com.fitness.activityservice.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class ActivityRequest {

    @NotBlank(message = "User id is required")
    private String userId;

    @NotBlank(message = "Activity type is required")
    private String type;

    @NotNull(message = "Duration is required")
    private Integer durationInMinutes;

    @NotNull(message = "Calories burned is required")
    private Integer caloriesBurned;
}

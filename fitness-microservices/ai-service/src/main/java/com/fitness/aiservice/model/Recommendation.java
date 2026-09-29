package com.fitness.aiservice.model;

import lombok.Data;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.LocalDateTime;

@Data
@Document(collection = "recommendations")
public class Recommendation {

    @Id
    private String id;
    private String activityId;
    private String userId;
    private String recommendation;
    private LocalDateTime createdAt;
}

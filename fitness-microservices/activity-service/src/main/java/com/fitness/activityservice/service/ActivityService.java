package com.fitness.activityservice.service;

import com.fitness.activityservice.dto.ActivityRequest;
import com.fitness.activityservice.dto.ActivityResponse;
import com.fitness.activityservice.event.ActivityEvent;
import com.fitness.activityservice.event.ActivityEventProducer;
import com.fitness.activityservice.exception.UserNotFoundException;
import com.fitness.activityservice.model.Activity;
import com.fitness.activityservice.repository.ActivityRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
public class ActivityService {

    private final ActivityRepository activityRepository;
    private final UserValidationService userValidationService;
    private final ActivityEventProducer activityEventProducer;

    public ActivityResponse createActivity(ActivityRequest request) {
        boolean userExists = userValidationService.validateUser(request.getUserId());
        if (!userExists) {
            throw new UserNotFoundException("User not found: " + request.getUserId());
        }

        Activity activity = new Activity();
        activity.setUserId(request.getUserId());
        activity.setType(request.getType());
        activity.setDurationInMinutes(request.getDurationInMinutes());
        activity.setCaloriesBurned(request.getCaloriesBurned());
        activity.setCreatedAt(LocalDateTime.now());

        Activity savedActivity = activityRepository.save(activity);

        publishActivityEvent(savedActivity);

        return toResponse(savedActivity);
    }

    public List<ActivityResponse> getAllActivities() {
        List<Activity> activities = activityRepository.findAll();

        return activities.stream()
                .map(this::toResponse)
                .toList();
    }

    private void publishActivityEvent(Activity activity) {
        ActivityEvent event = new ActivityEvent();
        event.setActivityId(activity.getId());
        event.setUserId(activity.getUserId());
        event.setType(activity.getType());
        event.setDurationInMinutes(activity.getDurationInMinutes());
        event.setCaloriesBurned(activity.getCaloriesBurned());
        activityEventProducer.publish(event);
    }

    private ActivityResponse toResponse(Activity activity) {
        ActivityResponse response = new ActivityResponse();
        response.setId(activity.getId());
        response.setUserId(activity.getUserId());
        response.setType(activity.getType());
        response.setDurationInMinutes(activity.getDurationInMinutes());
        response.setCaloriesBurned(activity.getCaloriesBurned());
        response.setCreatedAt(activity.getCreatedAt());
        return response;
    }
}

package com.fitness.activityservice.service;

import org.springframework.cloud.client.ServiceInstance;
import org.springframework.cloud.client.discovery.DiscoveryClient;
import org.springframework.stereotype.Service;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;

import java.util.List;

@Service
public class UserValidationService {

    private static final String USER_SERVICE_NAME = "user-service";

    private final DiscoveryClient discoveryClient;
    private final RestClient restClient;

    public UserValidationService(DiscoveryClient discoveryClient, RestClient.Builder restClientBuilder) {
        this.discoveryClient = discoveryClient;
        this.restClient = restClientBuilder.build();
    }

    public boolean validateUser(String userId) {
        String baseUrl = resolveUserServiceUrl();

        try {
            restClient.get()
                    .uri(baseUrl + "/api/users/{id}", userId)
                    .retrieve()
                    .toBodilessEntity();
            return true;
        } catch (HttpClientErrorException.NotFound e) {
            return false;
        } catch (ResourceAccessException e) {
            throw new IllegalStateException("User Service is unavailable, cannot validate user", e);
        }
    }

    private String resolveUserServiceUrl() {
        List<ServiceInstance> instances = discoveryClient.getInstances(USER_SERVICE_NAME);
        if (instances.isEmpty()) {
            throw new IllegalStateException("No instances available for " + USER_SERVICE_NAME);
        }
        return instances.get(0).getUri().toString();
    }
}

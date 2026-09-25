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

    private final DiscoveryClient discoveryClient;
    private final RestClient.Builder restClientBuilder;

    public UserValidationService(DiscoveryClient discoveryClient, RestClient.Builder restClientBuilder) {
        this.discoveryClient = discoveryClient;
        this.restClientBuilder = restClientBuilder;
    }

    public boolean validateUser(String userId) {
        List<ServiceInstance> instances = discoveryClient.getInstances("user-service");
        if (instances.isEmpty()) {
            throw new IllegalStateException("No instances available for user-service");
        }
        String baseUrl = instances.get(0).getUri().toString();

        try {
            restClientBuilder.build()
                    .get()
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
}

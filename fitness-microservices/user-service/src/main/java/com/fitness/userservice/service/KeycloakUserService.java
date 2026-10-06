package com.fitness.userservice.service;

import com.fitness.userservice.dto.RegisterRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClient;

import java.net.URI;
import java.util.List;
import java.util.Map;

@Slf4j
@Service
public class KeycloakUserService {

    private final RestClient restClient;
    private final String keycloakBaseUrl;
    private final String realm;
    private final String adminUsername;
    private final String adminPassword;

    public KeycloakUserService(RestClient.Builder restClientBuilder,
                                @Value("${keycloak.base-url}") String keycloakBaseUrl,
                                @Value("${keycloak.realm}") String realm,
                                @Value("${keycloak.admin.username}") String adminUsername,
                                @Value("${keycloak.admin.password}") String adminPassword) {
        this.restClient = restClientBuilder.build();
        this.keycloakBaseUrl = keycloakBaseUrl;
        this.realm = realm;
        this.adminUsername = adminUsername;
        this.adminPassword = adminPassword;
    }

    public String createUser(RegisterRequest request) {
        String adminToken = fetchAdminToken();

        Map<String, Object> credential = Map.of(
                "type", "password",
                "value", request.getPassword(),
                "temporary", false
        );

        Map<String, Object> keycloakUser = Map.of(
                "username", request.getEmail(),
                "email", request.getEmail(),
                "firstName", request.getFirstName() == null ? "" : request.getFirstName(),
                "lastName", request.getLastName() == null ? "" : request.getLastName(),
                "enabled", true,
                "emailVerified", true,
                "credentials", List.of(credential)
        );

        ResponseEntity<Void> response = restClient.post()
                .uri(keycloakBaseUrl + "/admin/realms/" + realm + "/users")
                .header("Authorization", "Bearer " + adminToken)
                .body(keycloakUser)
                .retrieve()
                .toBodilessEntity();

        String keycloakId = extractIdFromLocation(response.getHeaders().getLocation());

        log.info("Created Keycloak user for email {} with id {}", request.getEmail(), keycloakId);

        return keycloakId;
    }

    private String extractIdFromLocation(URI location) {
        String path = location.getPath();
        return path.substring(path.lastIndexOf('/') + 1);
    }

    private String fetchAdminToken() {
        MultiValueMap<String, String> body = new LinkedMultiValueMap<>();
        body.add("grant_type", "password");
        body.add("client_id", "admin-cli");
        body.add("username", adminUsername);
        body.add("password", adminPassword);

        Map<String, Object> response = restClient.post()
                .uri(keycloakBaseUrl + "/realms/master/protocol/openid-connect/token")
                .body(body)
                .retrieve()
                .body(Map.class);

        return (String) response.get("access_token");
    }
}

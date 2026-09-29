package com.fitness.aiservice.service;

import com.fitness.aiservice.dto.GeminiRequest;
import com.fitness.aiservice.dto.GeminiResponse;
import com.fitness.aiservice.event.ActivityEvent;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import java.util.List;

@Slf4j
@Service
public class GeminiService {

    private final RestClient restClient;
    private final String apiUrl;
    private final String apiKey;

    public GeminiService(RestClient.Builder restClientBuilder,
                          @Value("${gemini.api.url}") String apiUrl,
                          @Value("${gemini.api.key}") String apiKey) {
        this.restClient = restClientBuilder.build();
        this.apiUrl = apiUrl;
        this.apiKey = apiKey;
    }

    public String getRecommendation(ActivityEvent event) {
        String prompt = buildPrompt(event);

        GeminiRequest.Part part = new GeminiRequest.Part();
        part.setText(prompt);

        GeminiRequest.Content content = new GeminiRequest.Content();
        content.setParts(List.of(part));

        GeminiRequest request = new GeminiRequest();
        request.setContents(List.of(content));

        GeminiResponse response = restClient.post()
                .uri(apiUrl)
                .header("x-goog-api-key", apiKey)
                .body(request)
                .retrieve()
                .body(GeminiResponse.class);

        return extractText(response);
    }

    private String buildPrompt(ActivityEvent event) {
        return """
                Analyze this fitness activity and give a short, practical recommendation.
                Activity type: %s
                Duration: %d minutes
                Calories burned: %d

                Give a brief safety consideration and one suggestion for improvement.
                """.formatted(event.getType(), event.getDurationInMinutes(), event.getCaloriesBurned());
    }

    private String extractText(GeminiResponse response) {
        if (response == null || response.getCandidates() == null || response.getCandidates().isEmpty()) {
            log.warn("Gemini returned no candidates");
            return "No recommendation available.";
        }
        return response.getCandidates().get(0).getContent().getParts().get(0).getText();
    }
}

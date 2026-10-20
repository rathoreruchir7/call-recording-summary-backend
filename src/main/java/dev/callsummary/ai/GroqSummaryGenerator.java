package dev.callsummary.ai;

import java.util.List;
import java.util.Map;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import com.fasterxml.jackson.databind.JsonNode;

@Component
@ConditionalOnProperty(name = "app.ai.provider", havingValue = "groq")
public class GroqSummaryGenerator implements SummaryGenerator {
    private static final String SYSTEM_INSTRUCTIONS = "You are a call-summary service. Return only a concise summary of the transcript. Include key points, decisions, and action items. Do not ask the user for a transcript and do not invent details.";

    private final RestClient client;
    private final String model;

    public GroqSummaryGenerator(
            @Value("${app.groq.api-key}") String apiKey,
            @Value("${app.groq.summary-model}") String model) {
        if (apiKey.isBlank()) {
            throw new IllegalStateException("GROQ_API_KEY must be set when AI_PROVIDER is groq");
        }
        this.client = RestClient.builder()
                .baseUrl("https://api.groq.com/openai/v1")
                .defaultHeader(HttpHeaders.AUTHORIZATION, "Bearer " + apiKey)
                .build();
        this.model = model;
    }

    @Override
    public String summarize(String transcript, int maxOutputTokens) {
        Map<String, Object> request = Map.of(
                "model", model,
                "messages", List.of(
                        Map.of("role", "system", "content", SYSTEM_INSTRUCTIONS),
                        Map.of("role", "user", "content", "Transcript to summarize:\n---\n" + transcript + "\n---\nReturn only the summary.")),
                "max_completion_tokens", maxOutputTokens,
                "temperature", 0.2);

        JsonNode response = client.post()
                .uri("/chat/completions")
                .body(request)
                .retrieve()
                .body(JsonNode.class);
        String summary = response == null ? ""
                : response.path("choices").path(0).path("message").path("content").asText();
        if (summary.isBlank()) {
            throw new IllegalStateException("Groq returned an empty summary");
        }
        return summary;
    }
}
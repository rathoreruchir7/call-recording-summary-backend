package dev.callsummary;

import java.util.Map;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import com.fasterxml.jackson.databind.JsonNode;

@Component
@ConditionalOnProperty(name = "app.ai.provider", havingValue = "openai")
public class OpenAiSummaryGenerator implements SummaryGenerator {
    private final RestClient client;
    private final String model;

    public OpenAiSummaryGenerator(
            @Value("${app.openai.api-key}") String apiKey,
            @Value("${app.openai.summary-model}") String model) {
        this.client = RestClient.builder()
                .baseUrl("https://api.openai.com/v1")
                .defaultHeader(HttpHeaders.AUTHORIZATION, "Bearer " + apiKey)
                .build();
        this.model = model;
    }

    @Override
    public String summarize(String transcript, int maxOutputTokens) {
        Map<String, Object> request = Map.of(
                "model", model,
                "instructions", "Summarize this call transcript. Be concise. Include key points, decisions, and action items. Do not invent details.",
                "input", transcript,
                "max_output_tokens", maxOutputTokens,
                "store", false);

        JsonNode response = client.post()
                .uri("/responses")
                .body(request)
                .retrieve()
                .body(JsonNode.class);
        String summary = firstOutputText(response);
        if (summary.isBlank()) {
            throw new IllegalStateException("LLM provider returned an empty summary");
        }
        return summary;
    }

    private String firstOutputText(JsonNode response) {
        if (response == null) return "";
        for (JsonNode output : response.path("output")) {
            for (JsonNode content : output.path("content")) {
                if ("output_text".equals(content.path("type").asText())) {
                    return content.path("text").asText();
                }
            }
        }
        return "";
    }
}



package dev.callsummary.ai;

import java.io.IOException;
import java.io.InputStream;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.io.InputStreamResource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClient;

import com.fasterxml.jackson.databind.JsonNode;

@Component
@ConditionalOnProperty(name = "app.ai.provider", havingValue = "groq")
public class GroqSpeechToTextProvider implements SpeechToTextProvider {
    private final RestClient client;
    private final String model;

    public GroqSpeechToTextProvider(
            @Value("${app.groq.api-key}") String apiKey,
            @Value("${app.groq.transcription-model}") String model) {
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
    public String transcribe(InputStream audioStream, String contentType) throws IOException {
        InputStreamResource recording = new InputStreamResource(audioStream) {
            @Override
            public String getFilename() {
                return filenameFor(contentType);
            }

            @Override
            public long contentLength() {
                return -1;
            }
        };

        MultiValueMap<String, Object> form = new LinkedMultiValueMap<>();
        form.add("model", model);
        form.add("file", recording);

        JsonNode response = client.post()
                .uri("/audio/transcriptions")
                .contentType(MediaType.MULTIPART_FORM_DATA)
                .body(form)
                .retrieve()
                .body(JsonNode.class);
        String transcript = response == null ? "" : response.path("text").asText();
        if (transcript.isBlank()) {
            throw new IOException("Groq returned an empty transcript");
        }
        return transcript;
    }

    private String filenameFor(String contentType) {
        return switch (contentType) {
            case "audio/mpeg" -> "recording.mp3";
            case "audio/mp4", "audio/m4a", "audio/x-m4a" -> "recording.m4a";
            case "video/mp4" -> "recording.mp4";
            case "audio/ogg" -> "recording.ogg";
            default -> "recording.wav";
        };
    }
}
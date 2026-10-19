package dev.callsummary;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(name = "app.ai.provider", havingValue = "demo", matchIfMissing = true)
public class DemoSummaryGenerator implements SummaryGenerator {
    @Override
    public String summarize(String transcript, int maxOutputTokens) {
        int maxCharacters = maxOutputTokens * 4;
        int end = Math.min(transcript.length(), Math.min(160, maxCharacters));
        return "Demo summary: " + transcript.substring(0, end);
    }
}

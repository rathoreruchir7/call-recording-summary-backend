package dev.callsummary;

import org.springframework.stereotype.Component;

@Component
public class DemoSummaryGenerator implements SummaryGenerator {
    @Override
    public String summarize(String transcript) {
        int end = Math.min(transcript.length(), 160);
        return "Demo summary: " + transcript.substring(0, end);
    }
}

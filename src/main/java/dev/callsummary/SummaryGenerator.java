package dev.callsummary;

public interface SummaryGenerator {
    String summarize(String transcript, int maxOutputTokens);
}

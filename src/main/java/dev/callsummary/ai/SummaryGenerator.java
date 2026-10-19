package dev.callsummary.ai;


public interface SummaryGenerator {
    String summarize(String transcript, int maxOutputTokens);
}

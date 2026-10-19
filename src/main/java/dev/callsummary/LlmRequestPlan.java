package dev.callsummary;

public record LlmRequestPlan(
        int estimatedInputTokens,
        int maxOutputTokens,
        double estimatedCostUsd) {
}

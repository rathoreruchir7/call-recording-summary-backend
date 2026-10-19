package dev.callsummary.ai;


public record LlmRequestPlan(
        int estimatedInputTokens,
        int maxOutputTokens,
        double estimatedCostUsd) {
}

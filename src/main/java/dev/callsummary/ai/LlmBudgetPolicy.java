package dev.callsummary.ai;

import dev.callsummary.exception.LlmBudgetExceededException;


import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
public class LlmBudgetPolicy {
    private final int maxInputTokens;
    private final int maxOutputTokens;
    private final double maxEstimatedCostUsd;
    private final double inputCostPerMillionTokens;
    private final double outputCostPerMillionTokens;

    public LlmBudgetPolicy(
            @Value("${app.llm.max-input-tokens}") int maxInputTokens,
            @Value("${app.llm.max-output-tokens}") int maxOutputTokens,
            @Value("${app.llm.max-estimated-cost-usd}") double maxEstimatedCostUsd,
            @Value("${app.llm.input-cost-per-million-tokens}") double inputCostPerMillionTokens,
            @Value("${app.llm.output-cost-per-million-tokens}") double outputCostPerMillionTokens) {
        this.maxInputTokens = maxInputTokens;
        this.maxOutputTokens = maxOutputTokens;
        this.maxEstimatedCostUsd = maxEstimatedCostUsd;
        this.inputCostPerMillionTokens = inputCostPerMillionTokens;
        this.outputCostPerMillionTokens = outputCostPerMillionTokens;
    }

    public LlmRequestPlan validate(String transcript) {
        int inputTokens = estimateInputTokens(transcript);
        if (inputTokens > maxInputTokens) {
            throw new LlmBudgetExceededException("Transcript exceeds the input-token limit");
        }

        double inputCost = inputTokens * inputCostPerMillionTokens / 1_000_000d;
        double outputCost = maxOutputTokens * outputCostPerMillionTokens / 1_000_000d;
        double worstCaseCost = inputCost + outputCost;
        if (worstCaseCost > maxEstimatedCostUsd) {
            throw new LlmBudgetExceededException("Estimated LLM cost exceeds the per-call budget");
        }
        return new LlmRequestPlan(inputTokens, maxOutputTokens, worstCaseCost);
    }

    private int estimateInputTokens(String text) {
        int characters = text.codePointCount(0, text.length());
        return (int) Math.ceil(characters / 4d);
    }
}

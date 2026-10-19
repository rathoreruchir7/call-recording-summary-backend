package dev.callsummary;

public class LlmBudgetExceededException extends RuntimeException {
    public LlmBudgetExceededException(String message) {
        super(message);
    }
}

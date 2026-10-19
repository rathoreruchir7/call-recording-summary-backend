package dev.callsummary.exception;


public class LlmBudgetExceededException extends RuntimeException {
    public LlmBudgetExceededException(String message) {
        super(message);
    }
}

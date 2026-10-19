package dev.callsummary.exception;

public class InvalidRecordingException extends RuntimeException {
    public InvalidRecordingException(String message) {
        super(message);
    }
}
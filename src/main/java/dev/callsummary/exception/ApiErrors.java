package dev.callsummary.exception;

import java.util.Map;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.multipart.MaxUploadSizeExceededException;

@RestControllerAdvice
public class ApiErrors {
    @ExceptionHandler(InvalidRecordingException.class)
    ResponseEntity<Map<String, String>> invalid(InvalidRecordingException exception) {
        return ResponseEntity.badRequest().body(Map.of("error", exception.getMessage()));
    }

    @ExceptionHandler(MaxUploadSizeExceededException.class)
    ResponseEntity<Map<String, String>> tooLarge() {
        return ResponseEntity.status(HttpStatus.PAYLOAD_TOO_LARGE)
                .body(Map.of("error", "Recording exceeds upload limit"));
    }

    @ExceptionHandler(SummaryNotReadyException.class)
    ResponseEntity<Map<String, String>> summaryNotReady() {
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(Map.of("error", "Summary is not ready yet"));
    }

    @ExceptionHandler(CallNotFoundException.class)
    ResponseEntity<Map<String, String>> missing() {
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("error", "Call not found"));
    }
}
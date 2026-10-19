package dev.callsummary;

import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.multipart.MaxUploadSizeExceededException;

class InvalidRecordingException extends RuntimeException {
    InvalidRecordingException(String message) { super(message); }
}

class CallNotFoundException extends RuntimeException {}

class SummaryNotReadyException extends RuntimeException {}

class TranscriptTooLargeException extends RuntimeException {}

@RestControllerAdvice
public class ApiErrors {
    @ExceptionHandler(InvalidRecordingException.class)
    ResponseEntity<Map<String, String>> invalid(InvalidRecordingException e) {
        return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
    }

    @ExceptionHandler(MaxUploadSizeExceededException.class)
    ResponseEntity<Map<String, String>> tooLarge() {
        return ResponseEntity.status(HttpStatus.PAYLOAD_TOO_LARGE).body(Map.of("error", "Recording exceeds upload limit"));
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

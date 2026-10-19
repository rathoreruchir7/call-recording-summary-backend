package dev.callsummary.model;


import java.time.OffsetDateTime;
import java.util.UUID;

public record ProcessingJobRecord(
        UUID jobId,
        UUID callId,
        UUID userId,
        String status,
        int attempts,
        int maxAttempts,
        String lastError,
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt) {
}

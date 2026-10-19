package dev.callsummary.model;


import java.time.OffsetDateTime;
import java.util.UUID;

public record CallRecord(UUID callId, String status, long sizeBytes, String contentType,
                         OffsetDateTime createdAt) {}

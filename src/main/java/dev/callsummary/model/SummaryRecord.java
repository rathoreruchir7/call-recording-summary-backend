package dev.callsummary.model;


import java.time.OffsetDateTime;
import java.util.UUID;

public record SummaryRecord(
    UUID summaryId,
    UUID callId,
    UUID userId,
    String transcript,
    String summaryText,
    OffsetDateTime createdAt,
    OffsetDateTime updatedAt
) {}
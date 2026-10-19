package dev.callsummary.repository;

import dev.callsummary.model.SummaryRecord;


import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Optional;
import java.util.UUID;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class SummaryRepository {
    private final JdbcTemplate jdbc;

    public SummaryRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public SummaryRecord save(
            UUID callId,
            UUID userId,
            String transcript,
            String summaryText
    ) {
        UUID summaryId = UUID.randomUUID();

        jdbc.update("""
            INSERT INTO summaries(
                summary_id, call_id, user_id,
                transcript, summary_text
            )
            VALUES (?, ?, ?, ?, ?)
            """,
            summaryId,
            callId,
            userId,
            transcript,
            summaryText
        );

        return findByCallId(callId, userId).orElseThrow();
    }

    public Optional<SummaryRecord> findByCallId(UUID callId, UUID userId) {
        return jdbc.query("""
            SELECT summary_id, call_id, user_id, transcript,
                   summary_text, created_at, updated_at
              FROM summaries
             WHERE call_id = ? AND user_id = ?
            """, this::map, callId, userId)
            .stream()
            .findFirst();
    }

    private SummaryRecord map(ResultSet rs, int rowNumber)
            throws SQLException {
        return new SummaryRecord(
            rs.getObject("summary_id", UUID.class),
            rs.getObject("call_id", UUID.class),
            rs.getObject("user_id", UUID.class),
            rs.getString("transcript"),
            rs.getString("summary_text"),
            rs.getObject("created_at", java.time.OffsetDateTime.class),
            rs.getObject("updated_at", java.time.OffsetDateTime.class)
        );
    }
}
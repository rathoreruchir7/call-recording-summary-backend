package dev.callsummary;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;
import java.util.UUID;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class ProcessingJobRepository {
    private final JdbcTemplate jdbc;

    public ProcessingJobRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public void create(UUID callId, UUID userId) {
        jdbc.update("""
            INSERT INTO processing_jobs(job_id, call_id, user_id, status)
            VALUES (?, ?, ?, 'PENDING')
            ON CONFLICT (call_id) DO NOTHING
            """, UUID.randomUUID(), callId, userId);
    }

    public List<ProcessingJobRecord> findPending(int limit) {
        return jdbc.query("""
            SELECT job_id, call_id, user_id, status, attempts, max_attempts,
                   last_error, created_at, updated_at
              FROM processing_jobs
             WHERE status = 'PENDING' AND attempts < max_attempts
             ORDER BY created_at
             LIMIT ?
            """, this::map, limit);
    }

    public boolean claim(UUID callId, UUID userId) {
        int updated = jdbc.update("""
            UPDATE processing_jobs
               SET status = 'RUNNING', attempts = attempts + 1, updated_at = now()
             WHERE call_id = ? AND user_id = ?
               AND status = 'PENDING' AND attempts < max_attempts
            """, callId, userId);
        return updated == 1;
    }

    public void markSucceeded(UUID callId, UUID userId) {
        jdbc.update("""
            UPDATE processing_jobs
               SET status = 'SUCCEEDED', last_error = NULL, updated_at = now()
             WHERE call_id = ? AND user_id = ?
            """, callId, userId);
    }

    public void recordFailure(UUID callId, UUID userId, String error, boolean retryable) {
        jdbc.update("""
            UPDATE processing_jobs
               SET status = CASE WHEN ? = FALSE OR attempts >= max_attempts THEN 'FAILED' ELSE 'PENDING' END,
                   last_error = ?, updated_at = now()
             WHERE call_id = ? AND user_id = ?
            """, retryable, error, callId, userId);
    }

    public void requeueRunningJobs() {
        jdbc.update("""
            UPDATE processing_jobs
               SET status = 'PENDING', updated_at = now()
             WHERE status = 'RUNNING'
            """);
    }

    private ProcessingJobRecord map(ResultSet rs, int rowNumber) throws SQLException {
        return new ProcessingJobRecord(
                rs.getObject("job_id", UUID.class),
                rs.getObject("call_id", UUID.class),
                rs.getObject("user_id", UUID.class),
                rs.getString("status"),
                rs.getInt("attempts"),
                rs.getInt("max_attempts"),
                rs.getString("last_error"),
                rs.getObject("created_at", java.time.OffsetDateTime.class),
                rs.getObject("updated_at", java.time.OffsetDateTime.class));
    }
}


package dev.callsummary;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class CallRepository {
    private final JdbcTemplate jdbc;

    public CallRepository(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    public void insert(UUID callId, UUID userId, String objectKey, String contentType, long bytes, String sha256) {
        jdbc.update("""
            INSERT INTO calls(call_id, user_id, recording_object_key, content_type, size_bytes, sha256, status)
            VALUES (?, ?, ?, ?, ?, ?, 'UPLOADED')
            """, callId, userId, objectKey, contentType, bytes, sha256);
    }

    public Optional<CallRecord> find(UUID callId, UUID userId) {
        return jdbc.query("""
            SELECT call_id, status, size_bytes, content_type, created_at
              FROM calls WHERE call_id = ? AND user_id = ?
            """, this::map, callId, userId).stream().findFirst();
    }

    public Optional<String> findRecordingObjectKey(UUID callId, UUID userId) {
        return jdbc.queryForList("""
            SELECT recording_object_key
              FROM calls WHERE call_id = ? AND user_id = ?
            """, String.class, callId, userId).stream().findFirst();
    }

    public List<CallRecord> findAll(UUID userId) {
        return jdbc.query("""
            SELECT call_id, status, size_bytes, content_type, created_at
              FROM calls
             WHERE user_id = ?
             ORDER BY created_at DESC
            """, this::map, userId);
    }

    public void updateStatus(UUID callId, UUID userId, String status) {
        jdbc.update("""
            UPDATE calls
               SET status = ?
             WHERE call_id = ? AND user_id = ?
            """, status, callId, userId);
    }

    private CallRecord map(ResultSet rs, int rowNum) throws SQLException {
        return new CallRecord(rs.getObject("call_id", UUID.class), rs.getString("status"),
                rs.getLong("size_bytes"), rs.getString("content_type"),
                rs.getObject("created_at", java.time.OffsetDateTime.class));
    }
}

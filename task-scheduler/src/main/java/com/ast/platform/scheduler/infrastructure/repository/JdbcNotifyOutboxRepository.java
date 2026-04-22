package com.ast.platform.scheduler.infrastructure.repository;

import com.ast.platform.scheduler.domain.model.NotifyOutboxRecord;
import com.ast.platform.scheduler.domain.repository.NotifyOutboxRepository;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;

@Repository
public class JdbcNotifyOutboxRepository implements NotifyOutboxRepository {

    private static final RowMapper<NotifyOutboxRecord> ROW_MAPPER = new NotifyOutboxRowMapper();
    private final JdbcTemplate jdbcTemplate;

    public JdbcNotifyOutboxRepository(JdbcTemplate jdbcTemplate) { this.jdbcTemplate = jdbcTemplate; }

    @Override
    public NotifyOutboxRecord save(NotifyOutboxRecord record) {
        int updated = jdbcTemplate.update("""
                update scheduler_notify_outbox
                   set callback_url = ?, payload = ?, status = ?, trace_id = ?, retry_count = ?, next_retry_time = ?, last_error_message = ?, updated_at = ?
                 where outbox_id = ?
                """,
                record.callbackUrl(), record.payload(), record.status(), record.traceId(), record.retryCount(), timestamp(record.nextRetryTime()),
                record.lastErrorMessage(), timestamp(record.updatedAt()), record.outboxId());
        if (updated == 0) {
            jdbcTemplate.update("""
                    insert into scheduler_notify_outbox(outbox_id, task_id, callback_url, payload, status, trace_id, retry_count,
                    next_retry_time, last_error_message, created_at, updated_at)
                    values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                    """,
                    record.outboxId(), record.taskId(), record.callbackUrl(), record.payload(), record.status(), record.traceId(), record.retryCount(),
                    timestamp(record.nextRetryTime()), record.lastErrorMessage(), timestamp(record.createdAt()), timestamp(record.updatedAt()));
        }
        return record;
    }

    @Override
    public List<NotifyOutboxRecord> findDueRecords(String status, Instant now, int limit) {
        return jdbcTemplate.query(
                "select * from scheduler_notify_outbox where status = ? and (next_retry_time is null or next_retry_time <= ?) order by created_at asc limit ?",
                ROW_MAPPER,
                status,
                timestamp(now),
                limit);
    }

    private static Timestamp timestamp(Instant instant) { return instant == null ? null : Timestamp.from(instant); }

    private static final class NotifyOutboxRowMapper implements RowMapper<NotifyOutboxRecord> {
        @Override
        public NotifyOutboxRecord mapRow(ResultSet rs, int rowNum) throws SQLException {
            return new NotifyOutboxRecord(rs.getString("outbox_id"), rs.getString("task_id"), rs.getString("callback_url"),
                    rs.getString("payload"), rs.getString("status"), rs.getString("trace_id"), rs.getInt("retry_count"),
                    toInstant(rs.getTimestamp("next_retry_time")), rs.getString("last_error_message"),
                    rs.getTimestamp("created_at").toInstant(), rs.getTimestamp("updated_at").toInstant());
        }
        private static Instant toInstant(Timestamp ts) { return ts == null ? null : ts.toInstant(); }
    }
}
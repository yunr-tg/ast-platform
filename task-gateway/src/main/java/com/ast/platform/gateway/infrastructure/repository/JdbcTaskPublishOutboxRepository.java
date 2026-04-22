package com.ast.platform.gateway.infrastructure.repository;

import com.ast.platform.domain.task.TaskPublishOutboxStatus;
import com.ast.platform.gateway.domain.model.TaskPublishOutbox;
import com.ast.platform.gateway.domain.repository.TaskPublishOutboxRepository;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

@Repository
@ConditionalOnProperty(prefix = "ast.gateway", name = "storage-type", havingValue = "jdbc", matchIfMissing = true)
public class JdbcTaskPublishOutboxRepository implements TaskPublishOutboxRepository {

    private static final RowMapper<TaskPublishOutbox> ROW_MAPPER = new TaskPublishOutboxRowMapper();

    private final JdbcTemplate jdbcTemplate;

    public JdbcTaskPublishOutboxRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public TaskPublishOutbox save(TaskPublishOutbox outbox) {
        int updated = jdbcTemplate.update("""
                update gateway_publish_outbox
                   set task_id = ?, tenant_id = ?, task_type = ?, topic = ?, payload = ?,
                       status = ?, retry_count = ?, last_error_message = ?, next_retry_time = ?,
                       last_published_at = ?, updated_at = ?
                 where outbox_id = ?
                """,
                outbox.taskId(),
                outbox.tenantId(),
                outbox.taskType(),
                outbox.topic(),
                outbox.payload(),
                outbox.status().name(),
                outbox.retryCount(),
                outbox.lastErrorMessage(),
                timestamp(outbox.nextRetryTime()),
                timestamp(outbox.lastPublishedAt()),
                timestamp(outbox.updatedAt()),
                outbox.outboxId()
        );
        if (updated == 0) {
            jdbcTemplate.update("""
                    insert into gateway_publish_outbox(
                        outbox_id, task_id, tenant_id, task_type, topic, payload,
                        status, retry_count, last_error_message, next_retry_time, last_published_at, created_at, updated_at
                    ) values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                    """,
                    outbox.outboxId(),
                    outbox.taskId(),
                    outbox.tenantId(),
                    outbox.taskType(),
                    outbox.topic(),
                    outbox.payload(),
                    outbox.status().name(),
                    outbox.retryCount(),
                    outbox.lastErrorMessage(),
                    timestamp(outbox.nextRetryTime()),
                    timestamp(outbox.lastPublishedAt()),
                    timestamp(outbox.createdAt()),
                    timestamp(outbox.updatedAt())
            );
        }
        return outbox;
    }

    @Override
    public Optional<TaskPublishOutbox> findByTaskId(String taskId) {
        List<TaskPublishOutbox> results = jdbcTemplate.query(
                "select * from gateway_publish_outbox where task_id = ?",
                ROW_MAPPER,
                taskId
        );
        return results.stream().findFirst();
    }

    @Override
    public List<TaskPublishOutbox> findByStatus(TaskPublishOutboxStatus status, int limit) {
        return jdbcTemplate.query(
                "select * from gateway_publish_outbox where status = ? and (next_retry_time is null or next_retry_time <= ?) order by created_at asc limit ?",
                ROW_MAPPER,
                status.name(),
                timestamp(Instant.now()),
                limit
        );
    }

    private static Timestamp timestamp(Instant instant) {
        return instant == null ? null : Timestamp.from(instant);
    }

    private static final class TaskPublishOutboxRowMapper implements RowMapper<TaskPublishOutbox> {
        @Override
        public TaskPublishOutbox mapRow(ResultSet rs, int rowNum) throws SQLException {
            return new TaskPublishOutbox(
                    rs.getString("outbox_id"),
                    rs.getString("task_id"),
                    rs.getString("tenant_id"),
                    rs.getString("task_type"),
                    rs.getString("topic"),
                    rs.getString("payload"),
                    TaskPublishOutboxStatus.valueOf(rs.getString("status")),
                    rs.getInt("retry_count"),
                    rs.getString("last_error_message"),
                    toInstant(rs.getTimestamp("next_retry_time")),
                    toInstant(rs.getTimestamp("last_published_at")),
                    rs.getTimestamp("created_at").toInstant(),
                    rs.getTimestamp("updated_at").toInstant()
            );
        }

        private Instant toInstant(Timestamp timestamp) {
            return timestamp == null ? null : timestamp.toInstant();
        }
    }
}

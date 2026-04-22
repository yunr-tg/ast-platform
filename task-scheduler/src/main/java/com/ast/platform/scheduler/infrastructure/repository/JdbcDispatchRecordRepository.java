package com.ast.platform.scheduler.infrastructure.repository;

import com.ast.platform.scheduler.domain.model.DispatchRecord;
import com.ast.platform.scheduler.domain.repository.DispatchRecordRepository;
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
public class JdbcDispatchRecordRepository implements DispatchRecordRepository {

    private static final RowMapper<DispatchRecord> ROW_MAPPER = new DispatchRecordRowMapper();
    private final JdbcTemplate jdbcTemplate;

    public JdbcDispatchRecordRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public DispatchRecord save(DispatchRecord record) {
        int updated = jdbcTemplate.update("""
                update scheduler_dispatch
                   set tenant_id = ?, task_type = ?, worker_group = ?, worker_id = ?, dispatch_token = ?, dispatch_status = ?, trace_id = ?,
                       retry_count = ?, next_retry_time = ?, last_error_message = ?, last_result_payload = ?, last_dispatched_at = ?, updated_at = ?
                 where task_id = ?
                """,
                record.tenantId(), record.taskType(), record.workerGroup(), record.workerId(), record.dispatchToken(), record.dispatchStatus(), record.traceId(),
                record.retryCount(), timestamp(record.nextRetryTime()), record.lastErrorMessage(), record.lastResultPayload(),
                timestamp(record.lastDispatchedAt()), timestamp(record.updatedAt()), record.taskId());
        if (updated == 0) {
            jdbcTemplate.update("""
                    insert into scheduler_dispatch(task_id, tenant_id, task_type, worker_group, worker_id, dispatch_token,
                    dispatch_status, trace_id, retry_count, next_retry_time, last_error_message, last_result_payload, last_dispatched_at, updated_at, created_at)
                    values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                    """,
                    record.taskId(), record.tenantId(), record.taskType(), record.workerGroup(), record.workerId(), record.dispatchToken(),
                    record.dispatchStatus(), record.traceId(), record.retryCount(), timestamp(record.nextRetryTime()), record.lastErrorMessage(),
                    record.lastResultPayload(), timestamp(record.lastDispatchedAt()), timestamp(record.updatedAt()), timestamp(record.createdAt()));
        }
        return record;
    }

    @Override
    public Optional<DispatchRecord> findByTaskId(String taskId) {
        List<DispatchRecord> results = jdbcTemplate.query("select * from scheduler_dispatch where task_id = ?", ROW_MAPPER, taskId);
        return results.stream().findFirst();
    }

    @Override
    public List<DispatchRecord> findRetryDueRecords(Instant now, int limit) {
        return jdbcTemplate.query(
                "select * from scheduler_dispatch where dispatch_status = 'RETRY_WAIT' and next_retry_time <= ? order by next_retry_time asc limit ?",
                ROW_MAPPER,
                timestamp(now),
                limit);
    }

    @Override
    public List<DispatchRecord> findAll() {
        return jdbcTemplate.query("select * from scheduler_dispatch", ROW_MAPPER);
    }

    private static Timestamp timestamp(Instant instant) { return instant == null ? null : Timestamp.from(instant); }

    private static final class DispatchRecordRowMapper implements RowMapper<DispatchRecord> {
        @Override
        public DispatchRecord mapRow(ResultSet rs, int rowNum) throws SQLException {
            return new DispatchRecord(
                    rs.getString("task_id"), rs.getString("tenant_id"), rs.getString("task_type"), rs.getString("worker_group"),
                    rs.getString("worker_id"), rs.getString("dispatch_token"), rs.getString("dispatch_status"), rs.getString("trace_id"),
                    rs.getInt("retry_count"), toInstant(rs.getTimestamp("next_retry_time")), rs.getString("last_error_message"),
                    rs.getString("last_result_payload"), toInstant(rs.getTimestamp("last_dispatched_at")),
                    rs.getTimestamp("updated_at").toInstant(), rs.getTimestamp("created_at").toInstant());
        }
        private static Instant toInstant(Timestamp ts) { return ts == null ? null : ts.toInstant(); }
    }
}
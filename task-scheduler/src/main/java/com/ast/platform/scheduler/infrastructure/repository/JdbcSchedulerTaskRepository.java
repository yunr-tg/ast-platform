package com.ast.platform.scheduler.infrastructure.repository;

import com.ast.platform.domain.task.TaskStatus;
import com.ast.platform.scheduler.domain.model.SchedulerTaskSnapshot;
import com.ast.platform.scheduler.domain.repository.SchedulerTaskRepository;
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
public class JdbcSchedulerTaskRepository implements SchedulerTaskRepository {

    private static final RowMapper<SchedulerTaskSnapshot> ROW_MAPPER = new SchedulerTaskRowMapper();
    private final JdbcTemplate jdbcTemplate;

    public JdbcSchedulerTaskRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public Optional<SchedulerTaskSnapshot> findByTaskId(String taskId) {
        List<SchedulerTaskSnapshot> results = jdbcTemplate.query("select * from gateway_task where task_id = ?", ROW_MAPPER, taskId);
        return results.stream().findFirst();
    }

    @Override
    public List<SchedulerTaskSnapshot> findTasksByStatusInAndUpdatedBefore(java.util.Set<TaskStatus> statuses, Instant threshold, int limit) {
        String statusList = statuses.stream().map(s -> "'" + s.name() + "'").collect(java.util.stream.Collectors.joining(","));
        return jdbcTemplate.query(
                "select * from gateway_task where status in (" + statusList + ") and updated_at < ? limit ?",
                ROW_MAPPER, Timestamp.from(threshold), limit
        );
    }

    @Override
    public boolean advanceStatus(String taskId, TaskStatus expectedStatus, TaskStatus targetStatus) {
        int updated = jdbcTemplate.update(
                "update gateway_task set status = ?, version = version + 1, updated_at = ? where task_id = ? and status = ?",
                targetStatus.name(), Timestamp.from(Instant.now()), taskId, expectedStatus.name());
        return updated > 0;
    }

    @Override
    public void updateProgress(String taskId, int percentage) {
        jdbcTemplate.update("update gateway_task set progress = ?, updated_at = ? where task_id = ?",
                percentage, Timestamp.from(Instant.now()), taskId);
    }

    private static final class SchedulerTaskRowMapper implements RowMapper<SchedulerTaskSnapshot> {
        @Override
        public SchedulerTaskSnapshot mapRow(ResultSet rs, int rowNum) throws SQLException {
            return new SchedulerTaskSnapshot(
                    rs.getString("task_id"), rs.getString("tenant_id"), rs.getString("task_type"), rs.getString("worker_group"),
                    rs.getString("callback_url"), rs.getString("payload"), rs.getString("trace_id"),
                    TaskStatus.valueOf(rs.getString("status")), rs.getInt("priority"), rs.getInt("version"), rs.getTimestamp("updated_at").toInstant()
            );
        }
    }
}
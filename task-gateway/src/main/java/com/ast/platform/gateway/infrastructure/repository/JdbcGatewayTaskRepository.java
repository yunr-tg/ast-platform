package com.ast.platform.gateway.infrastructure.repository;

import com.ast.platform.common.exception.BusinessException;
import com.ast.platform.common.exception.ErrorCode;
import com.ast.platform.domain.task.TaskStatus;
import com.ast.platform.gateway.domain.model.GatewayTask;
import com.ast.platform.gateway.domain.repository.GatewayTaskRepository;
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
public class JdbcGatewayTaskRepository implements GatewayTaskRepository {

    private static final RowMapper<GatewayTask> ROW_MAPPER = new GatewayTaskRowMapper();

    private final JdbcTemplate jdbcTemplate;

    public JdbcGatewayTaskRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public Optional<GatewayTask> findByRequestId(String tenantId, String requestId) {
        return queryOne(
                "select * from gateway_task where tenant_id = ? and request_id = ?",
                tenantId, requestId
        );
    }

    @Override
    public Optional<GatewayTask> findByBizKey(String tenantId, String taskType, String bizKey) {
        return queryOne(
                "select * from gateway_task where tenant_id = ? and task_type = ? and biz_key = ?",
                tenantId, taskType, bizKey
        );
    }

    @Override
    public Optional<GatewayTask> findByTaskId(String taskId) {
        return queryOne("select * from gateway_task where task_id = ?", taskId);
    }

    @Override
    public GatewayTask save(GatewayTask task) {
        if (task.version() == 0) {
            jdbcTemplate.update("""
                    insert into gateway_task(
                        task_id, tenant_id, task_type, biz_key, request_id, worker_group, tag, payload,
                        callback_url, trace_id, status, priority, version, created_at, updated_at
                    ) values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                    """,
                    task.taskId(),
                    task.tenantId(),
                    task.taskType(),
                    task.bizKey(),
                    task.requestId(),
                    task.workerGroup(),
                    task.tag(),
                    task.payload(),
                    task.callbackUrl(),
                    task.traceId(),
                    task.status().name(),
                    task.priority(),
                    task.version(),
                    timestamp(task.createdAt()),
                    timestamp(task.updatedAt())
            );
            return task;
        }

        int updated = jdbcTemplate.update("""
                update gateway_task
                   set tenant_id = ?, task_type = ?, biz_key = ?, request_id = ?, worker_group = ?, tag = ?,
                       payload = ?, callback_url = ?, trace_id = ?, status = ?, priority = ?, version = ?, updated_at = ?
                 where task_id = ? and version = ?
                """,
                task.tenantId(),
                task.taskType(),
                task.bizKey(),
                task.requestId(),
                task.workerGroup(),
                task.tag(),
                task.payload(),
                task.callbackUrl(),
                task.traceId(),
                task.status().name(),
                task.priority(),
                task.version(),
                timestamp(task.updatedAt()),
                task.taskId(),
                task.version() - 1
        );
        if (updated == 0) {
            throw new BusinessException(
                    ErrorCode.OPTIMISTIC_LOCK_CONFLICT,
                    "Gateway task version conflict, taskId=" + task.taskId()
            );
        }
        return task;
    }

    private Optional<GatewayTask> queryOne(String sql, Object... args) {
        List<GatewayTask> results = jdbcTemplate.query(sql, ROW_MAPPER, args);
        return results.stream().findFirst();
    }

    private static Timestamp timestamp(Instant instant) {
        return Timestamp.from(instant);
    }

    private static final class GatewayTaskRowMapper implements RowMapper<GatewayTask> {
        @Override
        public GatewayTask mapRow(ResultSet rs, int rowNum) throws SQLException {
            return new GatewayTask(
                    rs.getString("task_id"),
                    rs.getString("tenant_id"),
                    rs.getString("task_type"),
                    rs.getString("biz_key"),
                    rs.getString("request_id"),
                    rs.getString("worker_group"),
                    rs.getString("tag"),
                    rs.getString("payload"),
                    rs.getString("callback_url"),
                    rs.getString("trace_id"),
                    TaskStatus.valueOf(rs.getString("status")),
                    rs.getInt("priority"),
                    rs.getInt("version"),
                    rs.getTimestamp("created_at").toInstant(),
                    rs.getTimestamp("updated_at").toInstant()
            );
        }
    }
}

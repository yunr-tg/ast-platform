package com.ast.platform.scheduler.infrastructure.repository;

import com.ast.platform.domain.worker.WorkerStatus;
import com.ast.platform.scheduler.domain.model.WorkerRuntimeSnapshot;
import com.ast.platform.scheduler.domain.repository.WorkerRuntimeRepository;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

@Repository
public class JdbcWorkerRuntimeRepository implements WorkerRuntimeRepository {

    private static final RowMapper<WorkerRuntimeSnapshot> ROW_MAPPER = new WorkerRuntimeRowMapper();
    private final JdbcTemplate jdbcTemplate;

    public JdbcWorkerRuntimeRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public WorkerRuntimeSnapshot save(WorkerRuntimeSnapshot snapshot) {
        int updated = jdbcTemplate.update("""
                update scheduler_worker
                   set worker_group = ?, host = ?, port = ?, protocol = ?, worker_version = ?, supported_task_types = ?,
                       tags = ?, status = ?, active_task_count = ?, max_concurrency = ?, available_slots = ?, avg_rt = ?,
                       error_rate = ?, weight = ?, cpu_usage = ?, memory_usage = ?, last_register_at = ?, last_heartbeat_at = ?, updated_at = ?
                 where worker_id = ?
                """,
                snapshot.workerGroup(), snapshot.host(), snapshot.port(), snapshot.protocol(), snapshot.workerVersion(),
                join(snapshot.supportedTaskTypes()), join(snapshot.tags()), snapshot.status().name(), snapshot.activeTaskCount(),
                snapshot.maxConcurrency(), snapshot.availableSlots(), snapshot.avgRt(), snapshot.errorRate(), snapshot.weight(),
                snapshot.cpuUsage(), snapshot.memoryUsage(),
                timestamp(snapshot.lastRegisterAt()), timestamp(snapshot.lastHeartbeatAt()), timestamp(snapshot.updatedAt()), snapshot.workerId());
        if (updated == 0) {
            jdbcTemplate.update("""
                    insert into scheduler_worker(worker_id, worker_group, host, port, protocol, worker_version,
                    supported_task_types, tags, status, active_task_count, max_concurrency, available_slots, avg_rt,
                    error_rate, weight, cpu_usage, memory_usage, last_register_at, last_heartbeat_at, updated_at)
                    values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                    """,
                    snapshot.workerId(), snapshot.workerGroup(), snapshot.host(), snapshot.port(), snapshot.protocol(), snapshot.workerVersion(),
                    join(snapshot.supportedTaskTypes()), join(snapshot.tags()), snapshot.status().name(), snapshot.activeTaskCount(),
                    snapshot.maxConcurrency(), snapshot.availableSlots(), snapshot.avgRt(), snapshot.errorRate(), snapshot.weight(),
                    snapshot.cpuUsage(), snapshot.memoryUsage(),
                    timestamp(snapshot.lastRegisterAt()), timestamp(snapshot.lastHeartbeatAt()), timestamp(snapshot.updatedAt()));
        }
        return snapshot;
    }

    @Override
    public Optional<WorkerRuntimeSnapshot> findByWorkerId(String workerId) {
        List<WorkerRuntimeSnapshot> results = jdbcTemplate.query("select * from scheduler_worker where worker_id = ?", ROW_MAPPER, workerId);
        return results.stream().findFirst();
    }

    @Override
    public List<WorkerRuntimeSnapshot> findDispatchableWorkers(String workerGroup, String taskType) {
        return jdbcTemplate.query("""
                select * from scheduler_worker 
                 where worker_group = ? 
                   and status in (?, ?) 
                   and available_slots > 0 
                 order by available_slots desc, error_rate asc, avg_rt asc, updated_at asc
                """,
                ROW_MAPPER, workerGroup, WorkerStatus.UP.name(), WorkerStatus.DEGRADED.name()
        ).stream().filter(worker -> worker.supports(taskType)).collect(Collectors.toList());
    }
    
    @Override
    public List<WorkerRuntimeSnapshot> findAll() {
        return jdbcTemplate.query("select * from scheduler_worker order by updated_at desc", ROW_MAPPER);
    }
    
    @Override
    public void deleteByWorkerId(String workerId) {
        jdbcTemplate.update("delete from scheduler_worker where worker_id = ?", workerId);
    }

    private static Timestamp timestamp(Instant instant) { return instant == null ? null : Timestamp.from(instant); }
    private static String join(List<String> values) { return values == null ? "" : String.join(",", values); }

    private static final class WorkerRuntimeRowMapper implements RowMapper<WorkerRuntimeSnapshot> {
        @Override
        public WorkerRuntimeSnapshot mapRow(ResultSet rs, int rowNum) throws SQLException {
            return new WorkerRuntimeSnapshot(
                    rs.getString("worker_id"), rs.getString("worker_group"), rs.getString("host"), (Integer) rs.getObject("port"),
                    rs.getString("protocol"), rs.getString("worker_version"), split(rs.getString("supported_task_types")),
                    split(rs.getString("tags")), WorkerStatus.valueOf(rs.getString("status")), rs.getInt("active_task_count"),
                    rs.getInt("max_concurrency"), rs.getInt("available_slots"), rs.getLong("avg_rt"), rs.getDouble("error_rate"),
                    rs.getInt("weight"), rs.getDouble("cpu_usage"), rs.getDouble("memory_usage"),
                    toInstant(rs.getTimestamp("last_register_at")), toInstant(rs.getTimestamp("last_heartbeat_at")), rs.getTimestamp("updated_at").toInstant());
        }
        private static List<String> split(String value) { return value == null || value.isBlank() ? List.of() : Arrays.asList(value.split(",")); }
        private static Instant toInstant(Timestamp ts) { return ts == null ? null : ts.toInstant(); }
    }
}

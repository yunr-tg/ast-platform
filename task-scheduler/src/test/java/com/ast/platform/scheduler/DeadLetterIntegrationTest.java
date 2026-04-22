package com.ast.platform.scheduler;

import com.ast.platform.contract.worker.WorkerCallbackRequest;
import com.ast.platform.domain.task.TaskStatus;
import com.ast.platform.scheduler.callback.application.WorkerCallbackApplicationService;
import com.ast.platform.scheduler.domain.model.DispatchRecord;
import com.ast.platform.scheduler.domain.repository.DispatchRecordRepository;
import com.ast.platform.scheduler.domain.repository.SchedulerTaskRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;

import java.sql.Timestamp;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:ast_scheduler_dlq;MODE=MySQL;DB_CLOSE_DELAY=-1;DATABASE_TO_UPPER=false",
        "ast.scheduler.callback.max-retry-count=2"
})
class DeadLetterIntegrationTest {

    @Autowired private JdbcTemplate jdbcTemplate;
    @Autowired private WorkerCallbackApplicationService workerCallbackApplicationService;
    @Autowired private SchedulerTaskRepository schedulerTaskRepository;
    @Autowired private DispatchRecordRepository dispatchRecordRepository;

    @BeforeEach
    void setUp() {
        jdbcTemplate.execute("delete from gateway_task");
        jdbcTemplate.execute("delete from scheduler_dispatch");
    }

    @Test
    void shouldMoveToDeadLetterAfterMaxRetries() {
        Instant now = Instant.now();
        String taskId = "task-dlq-1";
        String token = "token-1";
        String traceId = "trace-dlq";

        // 1. Setup task in DISPATCHED state with 2 retries already done
        jdbcTemplate.update("""
                insert into gateway_task(task_id, tenant_id, task_type, biz_key, request_id, worker_group, tag, payload, callback_url, trace_id, status, version, created_at, updated_at)
                values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """,
                taskId, "tenant-a", "type-1", "biz-1", "req-1", "group-1", "default", "{}",
                "http://cb", traceId, TaskStatus.DISPATCHED.name(), 0, Timestamp.from(now), Timestamp.from(now));

        dispatchRecordRepository.save(new DispatchRecord(
                taskId, "tenant-a", "type-1", "group-1", "worker-1", token, "RUNNING", traceId,
                2, // Current retry count is 2 (limit is also 2)
                null, null, null, now, now, now));

        // 2. Report failure again
        workerCallbackApplicationService.acceptCallback(new WorkerCallbackRequest(
                taskId, "worker-1", token, traceId, TaskStatus.FAILED, true, null, "Final fail"));

        // 3. Verify status is DEAD_LETTER
        assertThat(schedulerTaskRepository.findByTaskId(taskId).orElseThrow().status()).isEqualTo(TaskStatus.DEAD_LETTER);
        assertThat(dispatchRecordRepository.findByTaskId(taskId).orElseThrow().dispatchStatus()).isEqualTo("DEAD_LETTER");
    }

    @Test
    void shouldMoveToRetryWaitIfUnderLimit() {
        Instant now = Instant.now();
        String taskId = "task-retry-under-limit";
        String token = "token-2";
        String traceId = "trace-retry";

        jdbcTemplate.update("""
                insert into gateway_task(task_id, tenant_id, task_type, biz_key, request_id, worker_group, tag, payload, callback_url, trace_id, status, version, created_at, updated_at)
                values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """,
                taskId, "tenant-a", "type-1", "biz-2", "req-2", "group-1", "default", "{}",
                "http://cb", traceId, TaskStatus.DISPATCHED.name(), 0, Timestamp.from(now), Timestamp.from(now));

        dispatchRecordRepository.save(new DispatchRecord(
                taskId, "tenant-a", "type-1", "group-1", "worker-1", token, "RUNNING", traceId,
                1, // Current retry count is 1 (limit is 2)
                null, null, null, now, now, now));

        workerCallbackApplicationService.acceptCallback(new WorkerCallbackRequest(
                taskId, "worker-1", token, traceId, TaskStatus.FAILED, true, null, "Fail but retryable"));

        assertThat(schedulerTaskRepository.findByTaskId(taskId).orElseThrow().status()).isEqualTo(TaskStatus.RETRY_WAIT);
    }
}

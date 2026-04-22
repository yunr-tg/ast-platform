package com.ast.platform.scheduler;

import com.ast.platform.domain.task.TaskStatus;
import com.ast.platform.scheduler.domain.model.DispatchRecord;
import com.ast.platform.scheduler.domain.model.ReadyTaskEnvelope;
import com.ast.platform.scheduler.domain.repository.DispatchQueueRepository;
import com.ast.platform.scheduler.domain.repository.DispatchRecordRepository;
import com.ast.platform.scheduler.domain.repository.SchedulerTaskRepository;
import com.ast.platform.scheduler.retry.application.RetryApplicationService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:ast_scheduler_retry;MODE=MySQL;DB_CLOSE_DELAY=-1;DATABASE_TO_UPPER=false",
        "ast.scheduler.local-queue-enabled=true"
})
class SchedulerRetryIntegrationTest {

    @Autowired private JdbcTemplate jdbcTemplate;
    @Autowired private DispatchRecordRepository dispatchRecordRepository;
    @Autowired private RetryApplicationService retryApplicationService;
    @Autowired private SchedulerTaskRepository schedulerTaskRepository;
    @Autowired private DispatchQueueRepository dispatchQueueRepository;

    @Test
    void shouldMoveRetryTaskBackToReadyQueue() {
        Instant now = Instant.now();
        jdbcTemplate.update("""
                insert into gateway_task(task_id, tenant_id, task_type, biz_key, request_id, worker_group, tag, payload, callback_url, trace_id, status, version, created_at, updated_at)
                values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """,
                "task-retry-1", "tenant-r", "render-task", "biz-r-1", "req-r-1", "render-group", "default", "{}",
                "http://callback.test/retry", "trace-r-1", TaskStatus.RETRY_WAIT.name(), 1, Timestamp.from(now), Timestamp.from(now));

        dispatchRecordRepository.save(new DispatchRecord(
                "task-retry-1", "tenant-r", "render-task", "render-group", "worker-r", "token-r", "RETRY_WAIT", "trace-r-1", 1,
                now.minusSeconds(1), "err", "{}", now.minusSeconds(10), now, now));

        assertThat(retryApplicationService.moveRetryTasks()).isEqualTo(1);
        assertThat(schedulerTaskRepository.findByTaskId("task-retry-1").orElseThrow().status()).isEqualTo(TaskStatus.QUEUED);
        Optional<ReadyTaskEnvelope> envelope = dispatchQueueRepository.pollNextReadyTask();
        assertThat(envelope).isPresent();
        assertThat(envelope.get().taskId()).isEqualTo("task-retry-1");
    }
}

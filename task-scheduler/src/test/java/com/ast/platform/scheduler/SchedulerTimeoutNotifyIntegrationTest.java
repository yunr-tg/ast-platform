package com.ast.platform.scheduler;

import com.ast.platform.domain.task.TaskStatus;
import com.ast.platform.scheduler.compensator.application.CompensatorApplicationService;
import com.ast.platform.scheduler.domain.model.NotifyOutboxRecord;
import com.ast.platform.scheduler.domain.repository.NotifyOutboxRepository;
import com.ast.platform.scheduler.domain.repository.SchedulerTaskRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:ast_scheduler_timeout;MODE=MySQL;DB_CLOSE_DELAY=-1;DATABASE_TO_UPPER=false",
        "ast.scheduler.compensator.execution-timeout-ms=1000" // 1 second for test
})
class SchedulerTimeoutNotifyIntegrationTest {

    @Autowired private JdbcTemplate jdbcTemplate;
    @Autowired private CompensatorApplicationService compensatorApplicationService;
    @Autowired private SchedulerTaskRepository schedulerTaskRepository;
    @Autowired private NotifyOutboxRepository notifyOutboxRepository;

    @Test
    void shouldDetectTimeoutAndTriggerNotification() throws InterruptedException {
        // 1. Create a task in RUNNING status that is already "timed out"
        Instant longAgo = Instant.now().minusSeconds(10);
        String taskId = "task-timeout-1";
        jdbcTemplate.update("""
                insert into gateway_task(task_id, tenant_id, task_type, biz_key, request_id, worker_group, tag, payload, callback_url, trace_id, status, version, created_at, updated_at)
                values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """,
                taskId, "tenant-a", "render", "biz-t1", "req-t1", "group-1", "default", "{}",
                "http://callback.test/timeout", "trace-t1", TaskStatus.RUNNING.name(), 1, Timestamp.from(longAgo), Timestamp.from(longAgo));

        // 2. Run compensator
        compensatorApplicationService.compensate();

        // 3. Verify task status is now FAILED
        assertThat(schedulerTaskRepository.findByTaskId(taskId).orElseThrow().status()).isEqualTo(TaskStatus.FAILED);

        // 4. Verify notification is created
        List<NotifyOutboxRecord> dueRecords = notifyOutboxRepository.findDueRecords("NEW", Instant.now(), 10);
        assertThat(dueRecords).isNotEmpty();
        NotifyOutboxRecord record = dueRecords.stream()
                .filter(r -> r.taskId().equals(taskId))
                .findFirst()
                .orElseThrow();
        
        assertThat(record.callbackUrl()).isEqualTo("http://callback.test/timeout");
        assertThat(record.payload()).contains("Task execution timeout");
    }
}

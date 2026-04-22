package com.ast.platform.scheduler;

import com.ast.platform.contract.worker.WorkerRegisterRequest;
import com.ast.platform.domain.task.TaskStatus;
import com.ast.platform.scheduler.application.WorkerRegistrationApplicationService;
import com.ast.platform.scheduler.dispatcher.application.DispatcherApplicationService;
import com.ast.platform.scheduler.pump.application.PumpIngestApplicationService;
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
        "spring.datasource.url=jdbc:h2:mem:ast_scheduler_cancel;MODE=MySQL;DB_CLOSE_DELAY=-1;DATABASE_TO_UPPER=false",
        "ast.scheduler.local-queue-enabled=true"
})
class TaskCancellationIntegrationTest {

    @Autowired private JdbcTemplate jdbcTemplate;
    @Autowired private PumpIngestApplicationService pumpIngestApplicationService;
    @Autowired private WorkerRegistrationApplicationService workerRegistrationApplicationService;
    @Autowired private DispatcherApplicationService dispatcherApplicationService;
    @Autowired private SchedulerTaskRepository schedulerTaskRepository;

    @Test
    void shouldNotDispatchCancelledTask() {
        Instant now = Instant.now();
        // 1. Prepare a task in CANCELLED status in DB, but somehow it's in the Redis queue (e.g. cancelled after pump)
        jdbcTemplate.update("""
                insert into gateway_task(task_id, tenant_id, task_type, biz_key, request_id, worker_group, tag, payload, callback_url, trace_id, status, version, created_at, updated_at)
                values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """,
                "task-cancel-1", "tenant-a", "render-task", "biz-cancel-1", "req-cancel-1", "render-group", "default", "{}",
                null, "trace-cancel-1", TaskStatus.CANCELLED.name(), 1, Timestamp.from(now), Timestamp.from(now));

        // 2. Register a worker
        workerRegistrationApplicationService.registerWorker(new WorkerRegisterRequest(
                "worker-cancel", "render-group", "127.0.0.1", 19091, "http", "1.0.0", List.of("render-task"), List.of("default"), 4, 100));

        // 3. Move task to Redis queue (Pump simulates it being in MQ)
        pumpIngestApplicationService.acceptSubmittedTask("task-cancel-1", "tenant-a", "render-task", "render-group", "trace-cancel-1");

        // 4. Try to dispatch. Dispatcher should check DB, see CANCELLED, and skip it.
        boolean dispatched = dispatcherApplicationService.dispatchNext();
        
        // Should return false because it skipped the cancelled task and there are no other tasks
        assertThat(dispatched).isFalse();
        
        // Status should remain CANCELLED
        assertThat(schedulerTaskRepository.findByTaskId("task-cancel-1").orElseThrow().status()).isEqualTo(TaskStatus.CANCELLED);
    }
}

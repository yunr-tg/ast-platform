package com.ast.platform.scheduler;

import com.ast.platform.contract.worker.WorkerCallbackRequest;
import com.ast.platform.contract.worker.WorkerRegisterRequest;
import com.ast.platform.domain.task.TaskStatus;
import com.ast.platform.scheduler.application.WorkerRegistrationApplicationService;
import com.ast.platform.scheduler.callback.application.WorkerCallbackApplicationService;
import com.ast.platform.scheduler.dispatcher.application.DispatcherApplicationService;
import com.ast.platform.scheduler.domain.model.DispatchRecord;
import com.ast.platform.scheduler.domain.model.NotifyOutboxRecord;
import com.ast.platform.scheduler.domain.repository.DispatchRecordRepository;
import com.ast.platform.scheduler.domain.repository.NotifyOutboxRepository;
import com.ast.platform.scheduler.domain.repository.SchedulerTaskRepository;
import com.ast.platform.scheduler.pump.application.PumpIngestApplicationService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:ast_scheduler_dispatch;MODE=MySQL;DB_CLOSE_DELAY=-1;DATABASE_TO_UPPER=false",
        "ast.scheduler.local-queue-enabled=true"
})
class SchedulerDispatchCallbackIntegrationTest {

    @Autowired private JdbcTemplate jdbcTemplate;
    @Autowired private PumpIngestApplicationService pumpIngestApplicationService;
    @Autowired private WorkerRegistrationApplicationService workerRegistrationApplicationService;
    @Autowired private DispatcherApplicationService dispatcherApplicationService;
    @Autowired private WorkerCallbackApplicationService workerCallbackApplicationService;
    @Autowired private DispatchRecordRepository dispatchRecordRepository;
    @Autowired private SchedulerTaskRepository schedulerTaskRepository;
    @Autowired private NotifyOutboxRepository notifyOutboxRepository;

    @Test
    void shouldDispatchTaskAndAdvanceToSuccess() {
        Instant now = Instant.now();
        jdbcTemplate.update("""
                insert into gateway_task(task_id, tenant_id, task_type, biz_key, request_id, worker_group, tag, payload, callback_url, trace_id, status, version, created_at, updated_at)
                values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """,
                "task-1", "tenant-a", "render-task", "biz-1", "req-1", "render-group", "default", "{\"a\":1}",
                "http://callback.test/result", "trace-1", TaskStatus.QUEUED.name(), 0, Timestamp.from(now), Timestamp.from(now));

        workerRegistrationApplicationService.registerWorker(new WorkerRegisterRequest(
                "worker-a", "render-group", "127.0.0.1", 19091, "http", "1.0.0", List.of("render-task"), List.of("default"), 4, 100));
        pumpIngestApplicationService.acceptSubmittedTask("task-1", "tenant-a", "render-task", "render-group", "trace-1", 5);

        assertThat(dispatcherApplicationService.dispatchNext()).isTrue();
        assertThat(schedulerTaskRepository.findByTaskId("task-1").orElseThrow().status()).isEqualTo(TaskStatus.DISPATCHED);

        DispatchRecord dispatchRecord = dispatchRecordRepository.findByTaskId("task-1").orElseThrow();
        workerCallbackApplicationService.acceptCallback(new WorkerCallbackRequest(
                "task-1", "worker-a", dispatchRecord.dispatchToken(), "trace-1", TaskStatus.SUCCESS, false, "{\"ok\":true}", null));

        assertThat(schedulerTaskRepository.findByTaskId("task-1").orElseThrow().status()).isEqualTo(TaskStatus.SUCCESS);
        List<NotifyOutboxRecord> outboxes = notifyOutboxRepository.findDueRecords("NEW", Instant.now(), 10);
        assertThat(outboxes).hasSize(1);
        assertThat(outboxes.get(0).taskId()).isEqualTo("task-1");
    }
}
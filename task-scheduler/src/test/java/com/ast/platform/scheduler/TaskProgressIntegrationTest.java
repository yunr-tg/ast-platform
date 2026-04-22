package com.ast.platform.scheduler;

import com.ast.platform.contract.gateway.TaskProgressResponse;
import com.ast.platform.contract.worker.TaskProgressReportRequest;
import com.ast.platform.contract.worker.WorkerCallbackRequest;
import com.ast.platform.contract.worker.WorkerDispatchRequest;
import com.ast.platform.contract.worker.WorkerRegisterRequest;
import com.ast.platform.domain.task.TaskProgressRepository;
import com.ast.platform.domain.task.TaskStatus;
import com.ast.platform.scheduler.application.ProgressApplicationService;
import com.ast.platform.scheduler.application.WorkerRegistrationApplicationService;
import com.ast.platform.scheduler.dispatcher.application.DispatcherApplicationService;
import com.ast.platform.scheduler.domain.repository.DispatchRecordRepository;
import com.ast.platform.scheduler.pump.application.PumpIngestApplicationService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
public class TaskProgressIntegrationTest {

    @Autowired
    private ProgressApplicationService progressApplicationService;

    @Autowired
    private TaskProgressRepository taskProgressRepository;

    @Autowired
    private DispatcherApplicationService dispatcherApplicationService;

    @Autowired
    private PumpIngestApplicationService pumpIngestApplicationService;

    @Autowired
    private WorkerRegistrationApplicationService workerRegistrationApplicationService;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    void shouldReportAndQueryProgressWithThrottling() throws Exception {
        String taskId = "task-progress-1";
        String workerId = "worker-1";
        
        // 1. Setup task and worker
        setupTask(taskId);
        workerRegistrationApplicationService.registerWorker(new WorkerRegisterRequest(
                workerId, "group-1", "127.0.0.1", 8080, "http", "1.0.0", 
                List.of("type-1"), List.of(), 10, 100));

        // 2. Dispatch task
        pumpIngestApplicationService.acceptSubmittedTask(taskId, "tenant-1", "type-1", "group-1", "trace-1");
        dispatcherApplicationService.dispatchNext();

        // 3. Simulate Progress Reports
        long now = System.currentTimeMillis();
        
        // First report
        progressApplicationService.handleProgressReport(new TaskProgressReportRequest(
                taskId, workerId, 10, "Starting...", Map.of("step", 1), 1, now));
        
        // Out of order report (smaller sequence) - should be ignored
        progressApplicationService.handleProgressReport(new TaskProgressReportRequest(
                taskId, workerId, 5, "Late start", Map.of("step", 0), 0, now - 100));
        
        // Normal next report
        progressApplicationService.handleProgressReport(new TaskProgressReportRequest(
                taskId, workerId, 20, "Moving fast", Map.of("step", 2), 2, now + 1000));

        // Wait for async processing
        Thread.sleep(1000);

        // 5. Query latest progress
        var latest = taskProgressRepository.getLatestProgress(taskId).orElseThrow();
        assertThat(latest.percentage()).isEqualTo(20);
        assertThat(latest.message()).isEqualTo("Moving fast");
        assertThat(latest.payload()).containsEntry("step", 2);
    }

    private void setupTask(String taskId) {
        Instant now = Instant.now();
        jdbcTemplate.update("delete from gateway_task where task_id = ?", taskId);
        jdbcTemplate.update("""
                insert into gateway_task(task_id, tenant_id, task_type, biz_key, request_id, worker_group, tag, payload, callback_url, trace_id, status, version, created_at, updated_at)
                values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """,
                taskId, "tenant-1", "type-1", "biz-1", "req-1", "group-1", "default", "{}",
                null, "trace-1", TaskStatus.QUEUED.name(), 0, Timestamp.from(now), Timestamp.from(now));
    }
}

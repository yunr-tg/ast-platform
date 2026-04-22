package com.ast.platform.scheduler;

import com.ast.platform.contract.worker.WorkerRegisterRequest;
import com.ast.platform.domain.task.TaskStatus;
import com.ast.platform.scheduler.application.WorkerRegistrationApplicationService;
import com.ast.platform.scheduler.dispatcher.application.DispatcherApplicationService;
import com.ast.platform.scheduler.domain.model.DispatchRecord;
import com.ast.platform.scheduler.domain.repository.DispatchQueueRepository;
import com.ast.platform.scheduler.domain.repository.DispatchRecordRepository;
import com.ast.platform.scheduler.pump.application.PumpIngestApplicationService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:ast_scheduler_least_loaded;MODE=MySQL;DB_CLOSE_DELAY=-1;DATABASE_TO_UPPER=false",
        "ast.scheduler.local-queue-enabled=true",
        "ast.scheduler.worker-dispatch.type=logging"
})
class LeastLoadedDispatchIntegrationTest {

    @Autowired private JdbcTemplate jdbcTemplate;
    @Autowired private PumpIngestApplicationService pumpIngestApplicationService;
    @Autowired private WorkerRegistrationApplicationService workerRegistrationApplicationService;
    @Autowired private DispatcherApplicationService dispatcherApplicationService;
    @Autowired private DispatchQueueRepository dispatchQueueRepository;
    @Autowired private DispatchRecordRepository dispatchRecordRepository;

    @BeforeEach
    void setUp() {
        jdbcTemplate.execute("delete from gateway_task");
        jdbcTemplate.execute("delete from scheduler_worker");
        jdbcTemplate.execute("delete from scheduler_dispatch");
        while (dispatchQueueRepository.pollNextReadyTask().isPresent());
    }

    @Test
    void shouldPickLeastLoadedWorker() {
        Instant now = Instant.now();
        
        // 1. Prepare 3 Workers with different loads
        // Worker A: 10 slots, 2 busy -> 8 available
        registerWorker("worker-A", 10, 2, 100);
        // Worker B: 10 slots, 5 busy -> 5 available
        registerWorker("worker-B", 10, 5, 50);
        // Worker C: 10 slots, 1 busy -> 9 available (Least loaded!)
        registerWorker("worker-C", 10, 1, 200);

        // 2. Submit a task
        jdbcTemplate.update("""
                insert into gateway_task(task_id, tenant_id, task_type, biz_key, request_id, worker_group, tag, payload, callback_url, trace_id, status, version, created_at, updated_at)
                values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """,
                "task-ll-1", "tenant-a", "render", "biz-1", "req-1", "render-group", "default", "{}",
                "http://cb", "trace-ll", TaskStatus.QUEUED.name(), 0, Timestamp.from(now), Timestamp.from(now));
        
        pumpIngestApplicationService.acceptSubmittedTask("task-ll-1", "tenant-a", "render", "render-group", "trace-ll");

        // 3. Dispatch
        boolean dispatched = dispatcherApplicationService.dispatchNext();
        assertThat(dispatched).isTrue();

        // 4. Verify Worker C was picked
        DispatchRecord record = dispatchRecordRepository.findByTaskId("task-ll-1").orElseThrow();
        assertThat(record.workerId()).isEqualTo("worker-C");
    }

    @Test
    void shouldPickLowerRtWorkerWhenSlotsAreEqual() {
        Instant now = Instant.now();
        
        // Worker A: 10 slots, 2 busy, RT 100ms
        registerWorker("worker-A-rt", 10, 2, 100);
        // Worker B: 10 slots, 2 busy, RT 50ms (Better RT!)
        registerWorker("worker-B-rt", 10, 2, 50);

        jdbcTemplate.update("""
                insert into gateway_task(task_id, tenant_id, task_type, biz_key, request_id, worker_group, tag, payload, callback_url, trace_id, status, version, created_at, updated_at)
                values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """,
                "task-ll-2", "tenant-a", "render", "biz-2", "req-2", "render-group", "default", "{}",
                "http://cb", "trace-ll-2", TaskStatus.QUEUED.name(), 0, Timestamp.from(now), Timestamp.from(now));
        
        pumpIngestApplicationService.acceptSubmittedTask("task-ll-2", "tenant-a", "render", "render-group", "trace-ll-2");

        boolean dispatched = dispatcherApplicationService.dispatchNext();
        assertThat(dispatched).isTrue();

        DispatchRecord record = dispatchRecordRepository.findByTaskId("task-ll-2").orElseThrow();
        assertThat(record.workerId()).isEqualTo("worker-B-rt");
    }

    @Test
    void shouldPickLowerErrorRateWorkerWhenSlotsAreEqual() {
        Instant now = Instant.now();
        
        // Worker A: 10 slots, 2 busy, Error Rate 0.1
        registerWorker("worker-A-err", 10, 2, 100, 0.1);
        // Worker B: 10 slots, 2 busy, Error Rate 0.01 (Better Error Rate!)
        registerWorker("worker-B-err", 10, 2, 100, 0.01);

        jdbcTemplate.update("""
                insert into gateway_task(task_id, tenant_id, task_type, biz_key, request_id, worker_group, tag, payload, callback_url, trace_id, status, version, created_at, updated_at)
                values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """,
                "task-ll-3", "tenant-a", "render", "biz-3", "req-3", "render-group", "default", "{}",
                "http://cb", "trace-ll-3", TaskStatus.QUEUED.name(), 0, Timestamp.from(now), Timestamp.from(now));
        
        pumpIngestApplicationService.acceptSubmittedTask("task-ll-3", "tenant-a", "render", "render-group", "trace-ll-3");

        boolean dispatched = dispatcherApplicationService.dispatchNext();
        assertThat(dispatched).isTrue();

        DispatchRecord record = dispatchRecordRepository.findByTaskId("task-ll-3").orElseThrow();
        assertThat(record.workerId()).isEqualTo("worker-B-err");
    }

    private void registerWorker(String workerId, int max, int active, long avgRt) {
        registerWorker(workerId, max, active, avgRt, 0.0);
    }

    private void registerWorker(String workerId, int max, int active, long avgRt, double errorRate) {
        workerRegistrationApplicationService.registerWorker(new WorkerRegisterRequest(
                workerId, "render-group", "127.0.0.1", 1000, "http", "1.0.0", 
                List.of("render"), List.of("default"), max, 100));
        
        // Manually update load info since registerWorker sets defaults
        jdbcTemplate.update("""
            update scheduler_worker 
               set active_task_count = ?, available_slots = ?, avg_rt = ?, error_rate = ? 
             where worker_id = ?
            """, active, max - active, avgRt, errorRate, workerId);
    }
}

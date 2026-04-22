package com.ast.platform.scheduler;

import com.ast.platform.contract.worker.WorkerRegisterRequest;
import com.ast.platform.domain.task.SchedulingStrategy;
import com.ast.platform.domain.task.TaskStatus;
import com.ast.platform.scheduler.application.WorkerRegistrationApplicationService;
import com.ast.platform.scheduler.config.SchedulerProperties;
import com.ast.platform.scheduler.dispatcher.application.DispatcherApplicationService;
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
import java.util.Set;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

import com.ast.platform.contract.worker.WorkerHeartbeatRequest;
import com.ast.platform.domain.worker.WorkerStatus;
import com.ast.platform.scheduler.application.WorkerHeartbeatApplicationService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:ast_scheduler_strategy;MODE=MySQL;DB_CLOSE_DELAY=-1;DATABASE_TO_UPPER=false",
        "ast.scheduler.local-queue-enabled=true"
})
class SchedulingStrategyIntegrationTest {

    @Autowired private JdbcTemplate jdbcTemplate;
    @Autowired private PumpIngestApplicationService pumpIngestApplicationService;
    @Autowired private WorkerRegistrationApplicationService workerRegistrationApplicationService;
    @Autowired private WorkerHeartbeatApplicationService heartbeatApplicationService;
    @Autowired private DispatcherApplicationService dispatcherApplicationService;
    @Autowired private SchedulerProperties schedulerProperties;
    @Autowired private DispatchRecordRepository dispatchRecordRepository;

    @BeforeEach
    void setUp() {
        jdbcTemplate.execute("delete from gateway_task");
        jdbcTemplate.execute("delete from scheduler_worker");
        jdbcTemplate.execute("delete from scheduler_dispatch");
    }

    @Test
    void shouldDispatchUsingResourceAwareStrategy() {
        schedulerProperties.setStrategy(SchedulingStrategy.RESOURCE_AWARE);
        
        // worker-busy: high CPU
        workerRegistrationApplicationService.registerWorker(new WorkerRegisterRequest(
                "worker-busy", "group-1", "127.0.0.1", 1000, "http", "1.0.0", List.of("type-1"), List.of("tag-1"), 100, 100));
        heartbeatApplicationService.reportHeartbeat(new WorkerHeartbeatRequest(
                "worker-busy", "group-1", List.of("type-1"), WorkerStatus.UP, 10, 100, 90, 100L, 0.0, 0.9, 0.8));

        // worker-idle: low CPU
        workerRegistrationApplicationService.registerWorker(new WorkerRegisterRequest(
                "worker-idle", "group-1", "127.0.0.1", 1001, "http", "1.0.0", List.of("type-1"), List.of("tag-1"), 100, 100));
        heartbeatApplicationService.reportHeartbeat(new WorkerHeartbeatRequest(
                "worker-idle", "group-1", List.of("type-1"), WorkerStatus.UP, 0, 100, 100, 10L, 0.0, 0.1, 0.1));

        submitAndPump("task-res-1");
        dispatcherApplicationService.dispatchNext();

        String picked = dispatchRecordRepository.findAll().get(0).workerId();
        assertThat(picked).isEqualTo("worker-idle");
    }

    @Test
    void shouldDispatchUsingRandomStrategy() {
        schedulerProperties.setStrategy(SchedulingStrategy.RANDOM);
        prepareWorkers(5);
        
        for (int i = 0; i < 20; i++) {
            String taskId = "task-random-" + i;
            submitAndPump(taskId);
            dispatcherApplicationService.dispatchNext();
        }

        Set<String> pickedWorkers = dispatchRecordRepository.findAll().stream()
                .map(r -> r.workerId())
                .collect(Collectors.toSet());
        
        // With 20 tasks and 5 workers, RANDOM should pick most of them
        assertThat(pickedWorkers.size()).isGreaterThan(1);
    }

    @Test
    void shouldDispatchUsingRoundRobinStrategy() {
        schedulerProperties.setStrategy(SchedulingStrategy.ROUND_ROBIN);
        prepareWorkers(3); // worker-0, worker-1, worker-2

        for (int i = 0; i < 6; i++) {
            submitAndPump("task-rr-" + i);
            dispatcherApplicationService.dispatchNext();
        }

        List<String> pickedWorkers = dispatchRecordRepository.findAll().stream()
                .sorted((a, b) -> a.taskId().compareTo(b.taskId()))
                .map(r -> r.workerId())
                .toList();

        // Should be periodic: 0, 1, 2, 0, 1, 2 (or starting from some index)
        assertThat(pickedWorkers.get(0)).isNotEqualTo(pickedWorkers.get(1));
        assertThat(pickedWorkers.get(1)).isNotEqualTo(pickedWorkers.get(2));
        assertThat(pickedWorkers.get(0)).isEqualTo(pickedWorkers.get(3));
    }

    @Test
    void shouldDispatchUsingWeightedRandomStrategy() {
        schedulerProperties.setStrategy(SchedulingStrategy.WEIGHTED_RANDOM);
        
        // worker-heavy: weight 900
        workerRegistrationApplicationService.registerWorker(new WorkerRegisterRequest(
                "worker-heavy", "group-1", "127.0.0.1", 1000, "http", "1.0.0", List.of("type-1"), List.of("tag-1"), 100, 900));
        // worker-light: weight 100
        workerRegistrationApplicationService.registerWorker(new WorkerRegisterRequest(
                "worker-light", "group-1", "127.0.0.1", 1001, "http", "1.0.0", List.of("type-1"), List.of("tag-1"), 100, 100));

        for (int i = 0; i < 50; i++) {
            submitAndPump("task-weighted-" + i);
            dispatcherApplicationService.dispatchNext();
        }

        long heavyCount = dispatchRecordRepository.findAll().stream()
                .filter(r -> r.workerId().equals("worker-heavy"))
                .count();
        
        // 90% should be heavy. statistically > 35 out of 50
        assertThat(heavyCount).isGreaterThan(30);
    }

    @Test
    void shouldRouteToSpecificStrategyByTaskType() {
        // Global: RANDOM, Specific(type-1): ROUND_ROBIN
        schedulerProperties.setStrategy(SchedulingStrategy.RANDOM);
        schedulerProperties.getTypeStrategies().put("type-1", SchedulingStrategy.ROUND_ROBIN);
        
        prepareWorkers(3); // worker-0, 1, 2

        for (int i = 0; i < 6; i++) {
            submitAndPump("task-routed-" + i);
            dispatcherApplicationService.dispatchNext();
        }

        List<String> pickedWorkers = dispatchRecordRepository.findAll().stream()
                .sorted((a, b) -> a.taskId().compareTo(b.taskId()))
                .map(r -> r.workerId())
                .toList();

        // Should follow ROUND_ROBIN despite global RANDOM
        assertThat(pickedWorkers.get(0)).isNotEqualTo(pickedWorkers.get(1));
        assertThat(pickedWorkers.get(1)).isNotEqualTo(pickedWorkers.get(2));
        assertThat(pickedWorkers.get(0)).isEqualTo(pickedWorkers.get(3));
        
        // Cleanup for other tests
        schedulerProperties.getTypeStrategies().clear();
    }

    private void prepareWorkers(int count) {
        for (int i = 0; i < count; i++) {
            workerRegistrationApplicationService.registerWorker(new WorkerRegisterRequest(
                    "worker-" + i, "group-1", "127.0.0.1", 1000 + i, "http", "1.0.0", List.of("type-1"), List.of("tag-1"), 10, 100));
        }
    }

    private void submitAndPump(String taskId) {
        Instant now = Instant.now();
        jdbcTemplate.update("""
                insert into gateway_task(task_id, tenant_id, task_type, biz_key, request_id, worker_group, tag, payload, callback_url, trace_id, status, version, created_at, updated_at)
                values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """,
                taskId, "tenant-1", "type-1", "biz-" + taskId, "req-" + taskId, "group-1", "default", "{}",
                null, "trace-" + taskId, TaskStatus.QUEUED.name(), 0, Timestamp.from(now), Timestamp.from(now));
        
        pumpIngestApplicationService.acceptSubmittedTask(taskId, "tenant-1", "type-1", "group-1", "trace-" + taskId);
    }
}

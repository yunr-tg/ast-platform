package com.ast.platform.scheduler.compensator.application;

import io.micrometer.tracing.Span;
import io.micrometer.tracing.Tracer;
import com.ast.platform.domain.task.TaskStatus;
import com.ast.platform.scheduler.config.CompensatorProperties;
import com.ast.platform.scheduler.domain.model.CompensationAudit;
import com.ast.platform.scheduler.domain.model.NotifyOutboxRecord;
import com.ast.platform.scheduler.domain.model.ReadyTaskEnvelope;
import com.ast.platform.scheduler.domain.model.SchedulerTaskSnapshot;
import com.ast.platform.scheduler.domain.repository.CompensationAuditRepository;
import com.ast.platform.scheduler.domain.repository.DispatchQueueRepository;
import com.ast.platform.scheduler.domain.repository.NotifyOutboxRepository;
import com.ast.platform.scheduler.domain.repository.SchedulerTaskRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.Duration;
import java.util.List;
import java.util.Set;
import java.util.UUID;

@Service
public class CompensatorApplicationService {

    private static final Logger log = LoggerFactory.getLogger(CompensatorApplicationService.class);

    private final DispatchQueueRepository dispatchQueueRepository;
    private final SchedulerTaskRepository schedulerTaskRepository;
    private final NotifyOutboxRepository notifyOutboxRepository;
    private final CompensationAuditRepository compensationAuditRepository;
    private final CompensatorProperties compensatorProperties;
    private final Tracer tracer;

    public CompensatorApplicationService(DispatchQueueRepository dispatchQueueRepository,
                                         SchedulerTaskRepository schedulerTaskRepository,
                                         NotifyOutboxRepository notifyOutboxRepository,
                                         CompensationAuditRepository compensationAuditRepository,
                                         CompensatorProperties compensatorProperties,
                                         Tracer tracer) {
        this.dispatchQueueRepository = dispatchQueueRepository;
        this.schedulerTaskRepository = schedulerTaskRepository;
        this.notifyOutboxRepository = notifyOutboxRepository;
        this.compensationAuditRepository = compensationAuditRepository;
        this.compensatorProperties = compensatorProperties;
        this.tracer = tracer;
    }

    public void compensate() {
        Span span = tracer.nextSpan().name("compensate").start();
        try (Tracer.SpanInScope ws = tracer.withSpan(span)) {
            log.debug("Starting compensator scan...");
            
            // 1. Reclaim timeout tasks from processing queue (Redis side)
            reclaimRedisTimeoutTasks();
            
            // 2. Proactive timeout notification for stuck tasks (MySQL side)
            handleExecutionTimeoutTasks();
            
        } finally {
            span.end();
        }
    }

    private void reclaimRedisTimeoutTasks() {
        Instant threshold = Instant.now().minus(Duration.ofMillis(compensatorProperties.getDispatchTimeoutMs()));
        int reclaimed = dispatchQueueRepository.reclaimTimeoutTasks(threshold);
        if (reclaimed > 0) {
            log.info("Compensator reclaimed {} timeout tasks from processing queues", reclaimed);
            compensationAuditRepository.save(new CompensationAudit(
                    UUID.randomUUID().toString(),
                    null,
                    "RECLAIM_TIMEOUT_TASKS",
                    "Reclaimed " + reclaimed + " tasks with timeout threshold " + threshold,
                    Instant.now()
            ));
        }
    }

    private void handleExecutionTimeoutTasks() {
        Instant threshold = Instant.now().minus(Duration.ofMillis(compensatorProperties.getExecutionTimeoutMs()));
        List<SchedulerTaskSnapshot> timeoutTasks = schedulerTaskRepository.findTasksByStatusInAndUpdatedBefore(
                Set.of(TaskStatus.DISPATCHED, TaskStatus.RUNNING),
                threshold,
                100
        );

        for (SchedulerTaskSnapshot task : timeoutTasks) {
            log.warn("Task execution timeout detected, taskId={}, status={}", task.taskId(), task.status());
            
            // Atomically transition to FAILED
            boolean advanced = schedulerTaskRepository.advanceStatus(task.taskId(), task.status(), TaskStatus.FAILED);
            if (advanced) {
                // Trigger proactive notification
                if (task.callbackUrl() != null && !task.callbackUrl().isBlank()) {
                    String payload = buildTimeoutNotifyPayload(task.taskId());
                    notifyOutboxRepository.save(new NotifyOutboxRecord(
                            UUID.randomUUID().toString(),
                            task.taskId(),
                            task.callbackUrl(),
                            payload,
                            "NEW",
                            task.traceId(),
                            0,
                            null,
                            null,
                            Instant.now(),
                            Instant.now()
                    ));
                }
                
                compensationAuditRepository.save(new CompensationAudit(
                        UUID.randomUUID().toString(),
                        task.taskId(),
                        "EXECUTION_TIMEOUT_FAIL",
                        "Task failed due to execution timeout, previous status: " + task.status(),
                        Instant.now()
                ));
            }
        }
    }

    private String buildTimeoutNotifyPayload(String taskId) {
        return "{\"taskId\":\"" + taskId + "\",\"status\":\"FAILED\",\"errorMessage\":\"Task execution timeout\"}";
    }
}
package com.ast.platform.scheduler.retry.application;

import io.micrometer.tracing.Tracer;
import io.micrometer.tracing.Span;
import org.slf4j.MDC;
import com.ast.platform.domain.task.TaskStatus;
import com.ast.platform.scheduler.domain.model.DispatchRecord;
import com.ast.platform.scheduler.domain.model.ReadyTaskEnvelope;
import com.ast.platform.scheduler.domain.model.SchedulerTaskSnapshot;
import com.ast.platform.scheduler.domain.repository.DispatchQueueRepository;
import com.ast.platform.scheduler.domain.repository.DispatchRecordRepository;
import com.ast.platform.scheduler.domain.repository.SchedulerTaskRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.Instant;

@Service
public class RetryApplicationService {

    private static final Logger log = LoggerFactory.getLogger(RetryApplicationService.class);

    private final DispatchRecordRepository dispatchRecordRepository;
    private final DispatchQueueRepository dispatchQueueRepository;
    private final SchedulerTaskRepository schedulerTaskRepository;
    private final Tracer tracer;

    public RetryApplicationService(DispatchRecordRepository dispatchRecordRepository,
                                   DispatchQueueRepository dispatchQueueRepository,
                                   SchedulerTaskRepository schedulerTaskRepository,
                                   Tracer tracer) {
        this.dispatchRecordRepository = dispatchRecordRepository;
        this.dispatchQueueRepository = dispatchQueueRepository;
        this.schedulerTaskRepository = schedulerTaskRepository;
        this.tracer = tracer;
    }

    public int moveRetryTasks() {
        int moved = 0;
        for (DispatchRecord record : dispatchRecordRepository.findRetryDueRecords(Instant.now(), 100)) {
            Span span = tracer.nextSpan().name("retry-task").start();
            try (Tracer.SpanInScope ws = tracer.withSpan(span)) {
                MDC.put("traceId", record.traceId());
                if (schedulerTaskRepository.advanceStatus(record.taskId(), TaskStatus.RETRY_WAIT, TaskStatus.QUEUED)) {
                    // 获取任务的优先级
                    int priority = schedulerTaskRepository.findByTaskId(record.taskId())
                            .map(SchedulerTaskSnapshot::priority)
                            .orElse(5); // 默认优先级
                    dispatchQueueRepository.enqueueReady(new ReadyTaskEnvelope(record.taskId(), record.tenantId(), record.taskType(), record.workerGroup(), record.traceId(), priority, Instant.now()));
                    moved++;
                }
            } finally {
                span.end();
                MDC.remove("traceId");
            }
        }
        if (moved > 0) {
            log.info("retry tasks moved back to ready queue, count={}", moved);
        }
        return moved;
    }
}
package com.ast.platform.scheduler.callback.application;

import com.ast.platform.common.exception.BusinessException;
import com.ast.platform.common.exception.ErrorCode;
import com.ast.platform.contract.worker.WorkerCallbackRequest;
import com.ast.platform.domain.task.TaskProgressRepository;
import com.ast.platform.domain.task.TaskStateMachine;
import com.ast.platform.domain.task.TaskStatus;
import com.ast.platform.scheduler.config.SchedulerProperties;
import com.ast.platform.scheduler.domain.model.DispatchRecord;
import com.ast.platform.scheduler.domain.model.NotifyOutboxRecord;
import com.ast.platform.scheduler.domain.model.ReadyTaskEnvelope;
import com.ast.platform.scheduler.domain.model.SchedulerTaskSnapshot;
import com.ast.platform.scheduler.domain.repository.DispatchQueueRepository;
import com.ast.platform.scheduler.domain.repository.DispatchRecordRepository;
import com.ast.platform.scheduler.domain.repository.NotifyOutboxRepository;
import com.ast.platform.scheduler.domain.repository.SchedulerTaskRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.UUID;

@Service
public class WorkerCallbackApplicationService {

    private static final Logger log = LoggerFactory.getLogger(WorkerCallbackApplicationService.class);

    private final SchedulerTaskRepository schedulerTaskRepository;
    private final DispatchRecordRepository dispatchRecordRepository;
    private final DispatchQueueRepository dispatchQueueRepository;
    private final NotifyOutboxRepository notifyOutboxRepository;
    private final TaskProgressRepository taskProgressRepository;
    private final SchedulerProperties schedulerProperties;

    public WorkerCallbackApplicationService(SchedulerTaskRepository schedulerTaskRepository,
                                            DispatchRecordRepository dispatchRecordRepository,
                                            DispatchQueueRepository dispatchQueueRepository,
                                            NotifyOutboxRepository notifyOutboxRepository,
                                            TaskProgressRepository taskProgressRepository,
                                            SchedulerProperties schedulerProperties) {
        this.schedulerTaskRepository = schedulerTaskRepository;
        this.dispatchRecordRepository = dispatchRecordRepository;
        this.dispatchQueueRepository = dispatchQueueRepository;
        this.notifyOutboxRepository = notifyOutboxRepository;
        this.taskProgressRepository = taskProgressRepository;
        this.schedulerProperties = schedulerProperties;
    }

    public void acceptCallback(WorkerCallbackRequest request) {
        SchedulerTaskSnapshot task = schedulerTaskRepository.findByTaskId(request.taskId())
                .orElseThrow(() -> new BusinessException(ErrorCode.INVALID_REQUEST, "Task not found: " + request.taskId()));
        DispatchRecord dispatchRecord = dispatchRecordRepository.findByTaskId(request.taskId())
                .orElseThrow(() -> new BusinessException(ErrorCode.INVALID_REQUEST, "Dispatch record not found: " + request.taskId()));
        if (!request.dispatchToken().equals(dispatchRecord.dispatchToken())) {
            throw new BusinessException(ErrorCode.INVALID_REQUEST, "Dispatch token mismatch: " + request.taskId());
        }

        TaskStatus targetStatus = request.targetStatus();
        if (request.retryable() && targetStatus == TaskStatus.FAILED) {
            if (dispatchRecord.retryCount() >= schedulerProperties.getCallback().getMaxRetryCount()) {
                log.warn("Task exceeded max retry count, moving to DEAD_LETTER: {}", request.taskId());
                targetStatus = TaskStatus.DEAD_LETTER;
            } else {
                targetStatus = TaskStatus.RETRY_WAIT;
            }
        }
        
        TaskStateMachine.requireTransition(task.status(), targetStatus);
        
        if (task.status() == targetStatus) {
            return;
        }
        if (!schedulerTaskRepository.advanceStatus(task.taskId(), task.status(), targetStatus)) {
            throw new BusinessException(ErrorCode.OPTIMISTIC_LOCK_CONFLICT, "Task state update conflict: " + task.taskId());
        }

        Instant now = Instant.now();
        
        // Sync progress to MySQL on terminal state
        if (targetStatus == TaskStatus.SUCCESS || targetStatus == TaskStatus.FAILED || targetStatus == TaskStatus.DEAD_LETTER) {
            int finalPercentage = (targetStatus == TaskStatus.SUCCESS) ? 100 : 
                taskProgressRepository.getLatestProgress(task.taskId()).map(p -> p.percentage()).orElse(0);
            schedulerTaskRepository.updateProgress(task.taskId(), finalPercentage);
        }

        if (targetStatus == TaskStatus.RETRY_WAIT) {
            Instant nextRetryTime = now.plusSeconds(5);
            dispatchRecordRepository.save(dispatchRecord.withRetryScheduled(
                    dispatchRecord.retryCount() + 1, nextRetryTime, request.errorMessage(), request.resultPayload(), now));
            dispatchQueueRepository.enqueueRetry(new ReadyTaskEnvelope(task.taskId(), task.tenantId(), task.taskType(), task.workerGroup(), task.traceId(), task.priority(), now), nextRetryTime);
            log.info("callback accepted with retry, taskId={}, nextRetryTime={}", task.taskId(), nextRetryTime);
            return;
        }

        dispatchRecordRepository.save(dispatchRecord.withTerminal(targetStatus.name(), request.errorMessage(), request.resultPayload(), now));
        if (task.callbackUrl() != null && !task.callbackUrl().isBlank()) {
            notifyOutboxRepository.save(new NotifyOutboxRecord(
                    UUID.randomUUID().toString(), task.taskId(), task.callbackUrl(),
                    buildNotifyPayload(task.taskId(), targetStatus, request.resultPayload(), request.errorMessage()),
                    "NEW", task.traceId(), 0, null, null, now, now));
        }
        log.info("callback accepted, taskId={}, targetStatus={}", task.taskId(), targetStatus);
    }

    private String buildNotifyPayload(String taskId, TaskStatus targetStatus, String resultPayload, String errorMessage) {
        return "{\"taskId\":\"" + taskId + "\",\"status\":\"" + targetStatus.name() + "\",\"resultPayload\":"
                + quoted(resultPayload) + ",\"errorMessage\":" + quoted(errorMessage) + "}";
    }

    private String quoted(String value) {
        return value == null ? "null" : "\"" + value.replace("\"", "\\\"") + "\"";
    }
}
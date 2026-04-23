package com.ast.platform.gateway.application;

import io.micrometer.tracing.Tracer;
import com.ast.platform.contract.gateway.SubmitTaskRequest;
import com.ast.platform.contract.gateway.SubmitTaskResponse;
import com.ast.platform.domain.task.TaskPublishOutboxStatus;
import com.ast.platform.domain.task.TaskStateMachine;
import com.ast.platform.domain.task.TaskStatus;
import com.ast.platform.gateway.config.GatewayProperties;
import com.ast.platform.gateway.domain.gateway.TaskMessagePublisher;
import com.ast.platform.gateway.domain.model.GatewayTask;
import com.ast.platform.gateway.domain.model.TaskPublishOutbox;
import com.ast.platform.gateway.domain.repository.GatewayTaskRepository;
import com.ast.platform.gateway.domain.repository.TaskPublishOutboxRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Instant;
import java.util.UUID;

@Service
public class TaskSubmissionApplicationService {

    private static final Logger log = LoggerFactory.getLogger(TaskSubmissionApplicationService.class);

    private final GatewayTaskRepository taskRepository;
    private final TaskPublishOutboxRepository outboxRepository;
    private final TaskMessagePublisher taskMessagePublisher;
    private final GatewayProperties gatewayProperties;
    private final TransactionTemplate transactionTemplate;
    private final Tracer tracer;

    public TaskSubmissionApplicationService(GatewayTaskRepository taskRepository,
                                            TaskPublishOutboxRepository outboxRepository,
                                            TaskMessagePublisher taskMessagePublisher,
                                            GatewayProperties gatewayProperties,
                                            TransactionTemplate transactionTemplate,
                                            Tracer tracer) {
        this.taskRepository = taskRepository;
        this.outboxRepository = outboxRepository;
        this.taskMessagePublisher = taskMessagePublisher;
        this.gatewayProperties = gatewayProperties;
        this.transactionTemplate = transactionTemplate;
        this.tracer = tracer;
    }

    public SubmitTaskResponse submitTask(SubmitTaskRequest request) {
        return doSubmitTask(request);
    }

    public void cancelTask(com.ast.platform.contract.gateway.CancelTaskRequest request) {
        GatewayTask task = taskRepository.findByTaskId(request.taskId())
                .orElseThrow(() -> new com.ast.platform.common.exception.BusinessException(
                        com.ast.platform.common.exception.ErrorCode.INVALID_REQUEST,
                        "Task not found: " + request.taskId()
                ));

        if (!task.tenantId().equals(request.tenantId())) {
            throw new com.ast.platform.common.exception.BusinessException(
                    com.ast.platform.common.exception.ErrorCode.INVALID_REQUEST,
                    "Tenant ID mismatch: " + request.tenantId()
            );
        }

        if (task.status() == TaskStatus.CANCELLED) {
            return;
        }

        TaskStateMachine.requireTransition(task.status(), TaskStatus.CANCELLED);

        transactionTemplate.executeWithoutResult(status -> {
            taskRepository.save(task.withStatus(TaskStatus.CANCELLED, Instant.now()));
            log.info("task cancelled, tenantId={}, taskId={}, reason={}",
                    request.tenantId(), request.taskId(), request.reason());
        });
    }

    private synchronized SubmitTaskResponse doSubmitTask(SubmitTaskRequest request) {
        GatewayTask existingByRequestId = taskRepository.findByRequestId(request.tenantId(), request.requestId())
                .orElse(null);
        if (existingByRequestId != null) {
            return buildIdempotentResponse(existingByRequestId);
        }

        GatewayTask existingByBizKey = taskRepository.findByBizKey(
                request.tenantId(), request.taskType(), request.bizKey()
        ).orElse(null);
        if (existingByBizKey != null) {
            return buildIdempotentResponse(existingByBizKey);
        }

        Instant now = Instant.now();
        String traceId = tracer.currentSpan() != null ? tracer.currentSpan().context().traceId() : UUID.randomUUID().toString();

        PersistedSubmission persistedSubmission = transactionTemplate.execute(status -> {
            GatewayTask task = GatewayTask.create(
                    UUID.randomUUID().toString(),
                    request.tenantId(),
                    request.taskType(),
                    request.bizKey(),
                    request.requestId(),
                    request.workerGroup(),
                    request.tag(),
                    request.payload(),
                    request.callbackUrl(),
                    traceId,
                    request.priority(),
                    now
            );

            TaskPublishOutbox outbox = new TaskPublishOutbox(
                    UUID.randomUUID().toString(),
                    task.taskId(),
                    task.tenantId(),
                    task.taskType(),
                    gatewayProperties.getSubmitTopic(),
                    buildOutboxPayload(task),
                    TaskPublishOutboxStatus.NEW,
                    0,
                    null,
                    null,
                    null,
                    now,
                    now
            );

            taskRepository.save(task);
            outboxRepository.save(outbox);
            return new PersistedSubmission(task, outbox);
        });

        PublishResult publishResult = publishIfNecessary(
                persistedSubmission.task(),
                persistedSubmission.outbox()
        );
        log.info("gateway task submitted, tenantId={}, taskType={}, taskId={}, requestId={}, status={}, outboxStatus={}",
                publishResult.task().tenantId(), publishResult.task().taskType(),
                publishResult.task().taskId(), publishResult.task().requestId(),
                publishResult.task().status(), publishResult.outbox().status());
        return new SubmitTaskResponse(
                publishResult.task().taskId(),
                publishResult.task().status().name(),
                false,
                publishResult.outbox().status().name()
        );
    }

    public synchronized int republishPendingOutboxes(int batchSize) {
        int publishedCount = 0;
        publishedCount += republishByStatus(TaskPublishOutboxStatus.NEW, batchSize);
        if (publishedCount < batchSize) {
            publishedCount += republishByStatus(TaskPublishOutboxStatus.FAILED, batchSize - publishedCount);
        }
        return publishedCount;
    }

    private int republishByStatus(TaskPublishOutboxStatus status, int limit) {
        int publishedCount = 0;
        for (TaskPublishOutbox outbox : outboxRepository.findByStatus(status, limit)) {
            GatewayTask task = taskRepository.findByTaskId(outbox.taskId()).orElse(null);
            if (task == null) {
                continue;
            }
            // Use Micrometer Observation or just wrap in a new span
            tracer.nextSpan().name("republish").start();
            try {
                PublishResult publishResult = publishIfNecessary(task, outbox);
                if (publishResult.outbox().status() == TaskPublishOutboxStatus.SENT) {
                    publishedCount++;
                }
            } finally {
                if (tracer.currentSpan() != null) {
                    tracer.currentSpan().end();
                }
            }
        }
        return publishedCount;
    }

    private SubmitTaskResponse buildIdempotentResponse(GatewayTask task) {
        String outboxStatus = outboxRepository.findByTaskId(task.taskId())
                .map(outbox -> outbox.status().name())
                .orElse(TaskPublishOutboxStatus.NEW.name());
        return new SubmitTaskResponse(task.taskId(), task.status().name(), true, outboxStatus);
    }

    private PublishResult publishIfNecessary(GatewayTask task, TaskPublishOutbox outbox) {
        if (!gatewayProperties.isImmediatePublishEnabled()) {
            return new PublishResult(task, outbox);
        }

        boolean publishSuccess = taskMessagePublisher.publish(outbox);
        return transactionTemplate.execute(status -> updateAfterPublish(task, outbox, publishSuccess, Instant.now()));
    }

    private PublishResult updateAfterPublish(GatewayTask task,
                                             TaskPublishOutbox outbox,
                                             boolean publishSuccess,
                                             Instant now) {
        if (!publishSuccess) {
            TaskPublishOutbox failedOutbox = outboxRepository.save(
                    outbox.withPublishFailure(
                            "mock publish failed",
                            calculateNextRetryTime(outbox.retryCount(), now),
                            now
                    )
            );
            return new PublishResult(task, failedOutbox);
        }

        GatewayTask taskAfterPublish = task;
        if (task.status() == TaskStatus.INIT) {
            TaskStateMachine.requireTransition(task.status(), TaskStatus.QUEUED);
            taskAfterPublish = taskRepository.save(task.withStatus(TaskStatus.QUEUED, now));
        }
        TaskPublishOutbox sentOutbox = outboxRepository.save(outbox.withPublishSuccess(now));
        return new PublishResult(taskAfterPublish, sentOutbox);
    }

    private Instant calculateNextRetryTime(int currentRetryCount, Instant now) {
        long retryDelayMs = gatewayProperties.getOutboxBaseRetryDelayMs() * (1L << currentRetryCount);
        return now.plusMillis(retryDelayMs);
    }

    private String buildOutboxPayload(GatewayTask task) {
        return "{\"taskId\":\"" + task.taskId()
                + "\",\"tenantId\":\"" + task.tenantId()
                + "\",\"taskType\":\"" + task.taskType()
                + "\",\"workerGroup\":\"" + task.workerGroup()
                + "\",\"traceId\":\"" + task.traceId()
                + "\",\"priority\":" + task.priority()
                + "}";
    }

    private record PublishResult(GatewayTask task, TaskPublishOutbox outbox) {
    }

    private record PersistedSubmission(GatewayTask task, TaskPublishOutbox outbox) {
    }
}

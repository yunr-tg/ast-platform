package com.ast.platform.gateway.domain.model;

import com.ast.platform.domain.task.TaskPublishOutboxStatus;

import java.time.Instant;

public record TaskPublishOutbox(
        String outboxId,
        String taskId,
        String tenantId,
        String taskType,
        String topic,
        String payload,
        TaskPublishOutboxStatus status,
        int retryCount,
        String lastErrorMessage,
        Instant nextRetryTime,
        Instant lastPublishedAt,
        Instant createdAt,
        Instant updatedAt
) {

    public TaskPublishOutbox withPublishFailure(String errorMessage, Instant nextRetryTime, Instant now) {
        return new TaskPublishOutbox(
                outboxId,
                taskId,
                tenantId,
                taskType,
                topic,
                payload,
                TaskPublishOutboxStatus.FAILED,
                retryCount + 1,
                errorMessage,
                nextRetryTime,
                lastPublishedAt,
                createdAt,
                now
        );
    }

    public TaskPublishOutbox withPublishSuccess(Instant now) {
        return new TaskPublishOutbox(
                outboxId,
                taskId,
                tenantId,
                taskType,
                topic,
                payload,
                TaskPublishOutboxStatus.SENT,
                retryCount,
                null,
                null,
                now,
                createdAt,
                now
        );
    }
}

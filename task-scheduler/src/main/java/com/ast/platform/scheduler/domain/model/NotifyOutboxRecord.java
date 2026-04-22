package com.ast.platform.scheduler.domain.model;

import java.time.Instant;

public record NotifyOutboxRecord(
        String outboxId,
        String taskId,
        String callbackUrl,
        String payload,
        String status,
        String traceId,
        int retryCount,
        Instant nextRetryTime,
        String lastErrorMessage,
        Instant createdAt,
        Instant updatedAt
) {

    public NotifyOutboxRecord withStatus(String nextStatus, String errorMessage, Instant nextRetryTime, Instant now) {
        return new NotifyOutboxRecord(
                outboxId,
                taskId,
                callbackUrl,
                payload,
                nextStatus,
                traceId,
                "FAILED".equals(nextStatus) ? retryCount + 1 : retryCount,
                nextRetryTime,
                errorMessage,
                createdAt,
                now
        );
    }
}
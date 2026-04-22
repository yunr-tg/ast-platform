package com.ast.platform.scheduler.domain.model;

import java.time.Instant;

public record DispatchRecord(
        String taskId,
        String tenantId,
        String taskType,
        String workerGroup,
        String workerId,
        String dispatchToken,
        String dispatchStatus,
        String traceId,
        int retryCount,
        Instant nextRetryTime,
        String lastErrorMessage,
        String lastResultPayload,
        Instant lastDispatchedAt,
        Instant updatedAt,
        Instant createdAt
) {

    public DispatchRecord withDispatch(String targetWorkerId, String token, Instant now) {
        return new DispatchRecord(
                taskId,
                tenantId,
                taskType,
                workerGroup,
                targetWorkerId,
                token,
                "RUNNING",
                traceId,
                retryCount,
                null,
                null,
                null,
                now,
                now,
                createdAt
        );
    }

    public DispatchRecord withRetryScheduled(int nextRetryCount, Instant nextRetryTime, String errorMessage, String resultPayload, Instant now) {
        return new DispatchRecord(
                taskId,
                tenantId,
                taskType,
                workerGroup,
                workerId,
                dispatchToken,
                "RETRY_WAIT",
                traceId,
                nextRetryCount,
                nextRetryTime,
                errorMessage,
                resultPayload,
                lastDispatchedAt,
                now,
                createdAt
        );
    }

    public DispatchRecord withTerminal(String nextStatus, String errorMessage, String resultPayload, Instant now) {
        return new DispatchRecord(
                taskId,
                tenantId,
                taskType,
                workerGroup,
                workerId,
                dispatchToken,
                nextStatus,
                traceId,
                retryCount,
                null,
                errorMessage,
                resultPayload,
                lastDispatchedAt,
                now,
                createdAt
        );
    }
}
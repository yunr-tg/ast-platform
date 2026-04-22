package com.ast.platform.gateway.domain.model;

import com.ast.platform.domain.task.TaskStatus;

import java.time.Instant;

public record GatewayTask(
        String taskId,
        String tenantId,
        String taskType,
        String bizKey,
        String requestId,
        String workerGroup,
        String tag,
        String payload,
        String callbackUrl,
        String traceId,
        TaskStatus status,
        int version,
        Instant createdAt,
        Instant updatedAt
) {

    public GatewayTask withStatus(TaskStatus nextStatus, Instant now) {
        return new GatewayTask(
                taskId,
                tenantId,
                taskType,
                bizKey,
                requestId,
                workerGroup,
                tag,
                payload,
                callbackUrl,
                traceId,
                nextStatus,
                version + 1,
                createdAt,
                now
        );
    }
}

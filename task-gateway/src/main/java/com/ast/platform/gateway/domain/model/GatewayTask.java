package com.ast.platform.gateway.domain.model;

import com.ast.platform.domain.task.TaskPriority;
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
        int priority,
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
                priority,
                version + 1,
                createdAt,
                now
        );
    }
    
    public static GatewayTask create(
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
            Integer priority,
            Instant now
    ) {
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
                TaskStatus.INIT,
                TaskPriority.fromValueOrDefault(priority).getValue(),
                0,
                now,
                now
        );
    }
}

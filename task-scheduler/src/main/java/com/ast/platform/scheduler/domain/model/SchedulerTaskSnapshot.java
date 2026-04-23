package com.ast.platform.scheduler.domain.model;

import com.ast.platform.domain.task.TaskStatus;

import java.time.Instant;

public record SchedulerTaskSnapshot(
        String taskId,
        String tenantId,
        String taskType,
        String workerGroup,
        String callbackUrl,
        String payload,
        String traceId,
        TaskStatus status,
        int priority,
        int version,
        Instant updatedAt
) {
}
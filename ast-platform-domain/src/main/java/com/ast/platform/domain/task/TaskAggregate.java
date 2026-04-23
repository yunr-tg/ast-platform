package com.ast.platform.domain.task;

import java.time.Instant;

public record TaskAggregate(
        String taskId,
        String tenantId,
        String taskType,
        String workerGroup,
        String dispatchToken,
        TaskStatus status,
        int priority,
        int retryCount,
        int version,
        Instant createdAt,
        Instant updatedAt
) {
}

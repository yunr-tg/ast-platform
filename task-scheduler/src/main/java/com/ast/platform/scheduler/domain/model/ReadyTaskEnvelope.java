package com.ast.platform.scheduler.domain.model;

import java.time.Instant;

public record ReadyTaskEnvelope(
        String taskId,
        String tenantId,
        String taskType,
        String workerGroup,
        String traceId,
        Instant enqueuedAt
) {

    public String activeKey() {
        return tenantId + "::" + taskType;
    }
}
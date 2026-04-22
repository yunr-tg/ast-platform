package com.ast.platform.scheduler.domain.model;

import java.time.Instant;

public record CompensationAudit(
        String auditId,
        String taskId,
        String action,
        String detail,
        Instant createdAt
) {
}

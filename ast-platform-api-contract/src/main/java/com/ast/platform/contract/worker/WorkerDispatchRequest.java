package com.ast.platform.contract.worker;

import jakarta.validation.constraints.NotBlank;
import java.time.Instant;

public record WorkerDispatchRequest(
        @NotBlank String taskId,
        @NotBlank String tenantId,
        @NotBlank String taskType,
        String payload,
        @NotBlank String dispatchToken,
        @NotBlank String traceId,
        Instant dispatchedAt
) {
}

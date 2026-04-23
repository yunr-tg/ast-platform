package com.ast.platform.contract.gateway;

import jakarta.validation.constraints.NotBlank;

public record SubmitTaskRequest(
        @NotBlank String tenantId,
        @NotBlank String taskType,
        @NotBlank String bizKey,
        @NotBlank String requestId,
        @NotBlank String workerGroup,
        String tag,
        String payload,
        String callbackUrl,
        String traceId,
        Integer priority
) {
}

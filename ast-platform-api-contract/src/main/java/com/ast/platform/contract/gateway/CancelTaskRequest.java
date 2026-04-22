package com.ast.platform.contract.gateway;

import jakarta.validation.constraints.NotBlank;

public record CancelTaskRequest(
        @NotBlank String tenantId,
        @NotBlank String taskId,
        String reason
) {
}

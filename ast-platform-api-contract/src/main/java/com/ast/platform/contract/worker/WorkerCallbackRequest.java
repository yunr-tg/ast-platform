package com.ast.platform.contract.worker;

import com.ast.platform.domain.task.TaskStatus;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record WorkerCallbackRequest(
        @NotBlank String taskId,
        @NotBlank String workerId,
        @NotBlank String dispatchToken,
        @NotBlank String traceId,
        @NotNull TaskStatus targetStatus,
        boolean retryable,
        String resultPayload,
        String errorMessage
) {
}

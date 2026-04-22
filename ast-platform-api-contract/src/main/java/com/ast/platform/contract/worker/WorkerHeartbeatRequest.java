package com.ast.platform.contract.worker;

import com.ast.platform.domain.worker.WorkerStatus;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;

import java.util.List;

public record WorkerHeartbeatRequest(
        @NotBlank String workerId,
        @NotBlank String workerGroup,
        @NotEmpty List<String> supportedTaskTypes,
        @NotNull WorkerStatus status,
        @PositiveOrZero int activeTaskCount,
        @PositiveOrZero int maxConcurrency,
        @PositiveOrZero int availableSlots,
        @PositiveOrZero long avgRt,
        @PositiveOrZero double errorRate,
        Double cpuUsage,
        Double memoryUsage
) {
}

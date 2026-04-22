package com.ast.platform.contract.worker;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import java.util.Map;

/**
 * Request from Worker to Scheduler to report task progress.
 */
public record TaskProgressReportRequest(
        @NotBlank String taskId,
        @NotBlank String workerId,
        @Min(0) @Max(100) int percentage,
        String message,
        Map<String, Object> payload,
        long sequence,
        long timestamp
) {
}

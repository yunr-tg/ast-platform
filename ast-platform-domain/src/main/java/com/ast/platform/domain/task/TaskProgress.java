package com.ast.platform.domain.task;

import java.util.Map;

/**
 * Domain object representing task progress.
 */
public record TaskProgress(
        String taskId,
        int percentage,
        String message,
        Map<String, Object> payload,
        long timestamp
) {
}

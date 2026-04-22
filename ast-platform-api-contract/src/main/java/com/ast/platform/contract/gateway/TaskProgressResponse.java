package com.ast.platform.contract.gateway;

import java.util.Map;

/**
 * Response for task progress query.
 */
public record TaskProgressResponse(
        String taskId,
        int percentage,
        String message,
        Map<String, Object> payload,
        long timestamp
) {
}

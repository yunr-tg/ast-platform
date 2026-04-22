package com.ast.platform.contract.gateway;

public record SubmitTaskResponse(
        String taskId,
        String status,
        boolean idempotent,
        String outboxStatus
) {
}

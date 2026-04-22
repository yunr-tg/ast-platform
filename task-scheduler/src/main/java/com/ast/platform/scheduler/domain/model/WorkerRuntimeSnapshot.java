package com.ast.platform.scheduler.domain.model;

import com.ast.platform.domain.worker.WorkerStatus;

import java.time.Instant;
import java.util.List;

public record WorkerRuntimeSnapshot(
        String workerId,
        String workerGroup,
        String host,
        Integer port,
        String protocol,
        String workerVersion,
        List<String> supportedTaskTypes,
        List<String> tags,
        WorkerStatus status,
        int activeTaskCount,
        int maxConcurrency,
        int availableSlots,
        long avgRt,
        double errorRate,
        int weight,
        double cpuUsage,
        double memoryUsage,
        Instant lastRegisterAt,
        Instant lastHeartbeatAt,
        Instant updatedAt
) {

    public boolean supports(String taskType) {
        return supportedTaskTypes.contains(taskType);
    }
}
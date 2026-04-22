package com.ast.platform.infra.redis;

public final class DispatchRedisKeys {

    private DispatchRedisKeys() {
    }

    public static String readyQueue(String tenantId, String taskType) {
        return "dispatch:ready:" + tenantId + ":" + taskType;
    }

    public static String processingQueue(String tenantId, String taskType) {
        return "dispatch:processing:" + tenantId + ":" + taskType;
    }

    public static String retryQueue(String tenantId, String taskType) {
        return "dispatch:retry:" + tenantId + ":" + taskType;
    }

    public static String activeKeys() {
        return "dispatch:active:keys";
    }

    public static String workerGroup(String workerGroup) {
        return "ats:worker:group:" + workerGroup;
    }

    public static String workerMeta(String workerId) {
        return "ats:worker:meta:" + workerId;
    }

    public static String workerRuntime(String workerId) {
        return "ats:worker:runtime:" + workerId;
    }
}

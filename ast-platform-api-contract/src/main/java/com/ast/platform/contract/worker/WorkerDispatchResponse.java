package com.ast.platform.contract.worker;

public record WorkerDispatchResponse(
        boolean success,
        String errorCode,
        String message
) {
    public static WorkerDispatchResponse ok() {
        return new WorkerDispatchResponse(true, null, "Accepted");
    }

    public static WorkerDispatchResponse fail(String errorCode, String message) {
        return new WorkerDispatchResponse(false, errorCode, message);
    }
}

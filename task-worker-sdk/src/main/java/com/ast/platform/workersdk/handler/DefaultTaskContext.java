package com.ast.platform.workersdk.handler;

import java.util.Map;

public class DefaultTaskContext implements TaskContext {

    private final String taskId;
    private final String payload;
    private final ProgressReporter progressReporter;

    public DefaultTaskContext(String taskId, String payload, ProgressReporter progressReporter) {
        this.taskId = taskId;
        this.payload = payload;
        this.progressReporter = progressReporter;
    }

    @Override
    public String taskId() {
        return taskId;
    }

    @Override
    public String payload() {
        return payload;
    }

    @Override
    public void reportProgress(int percentage, String message) {
        progressReporter.report(percentage, message, null);
    }

    @Override
    public void reportProgress(int percentage, String message, Map<String, Object> payload) {
        progressReporter.report(percentage, message, payload);
    }
}

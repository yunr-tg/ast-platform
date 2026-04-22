package com.ast.platform.workersdk.handler;

import java.util.Map;

/**
 * Execution context for a task, providing utilities like progress reporting.
 */
public interface TaskContext {

    String taskId();

    String payload();

    /**
     * Report task progress.
     * 
     * @param percentage 0-100
     * @param message    optional message
     */
    void reportProgress(int percentage, String message);

    /**
     * Report task progress with custom structured payload.
     * 
     * @param percentage 0-100
     * @param message    optional message
     * @param payload    custom structured data
     */
    void reportProgress(int percentage, String message, Map<String, Object> payload);
}

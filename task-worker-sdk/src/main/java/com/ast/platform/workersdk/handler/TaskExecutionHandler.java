package com.ast.platform.workersdk.handler;

public interface TaskExecutionHandler {

    String taskType();

    void handle(TaskContext context);
}

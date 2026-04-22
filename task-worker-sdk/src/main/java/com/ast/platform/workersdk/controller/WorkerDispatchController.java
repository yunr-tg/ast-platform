package com.ast.platform.workersdk.controller;

import io.micrometer.tracing.Tracer;
import io.micrometer.tracing.Span;
import org.slf4j.MDC;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import com.ast.platform.contract.worker.WorkerCallbackRequest;
import com.ast.platform.contract.worker.WorkerDispatchRequest;
import com.ast.platform.contract.worker.WorkerDispatchResponse;
import com.ast.platform.domain.task.TaskStatus;
import com.ast.platform.workersdk.client.WorkerControlPlaneClient;
import com.ast.platform.workersdk.config.WorkerSdkProperties;
import com.ast.platform.workersdk.handler.DefaultTaskContext;
import com.ast.platform.workersdk.handler.ProgressReporter;
import com.ast.platform.workersdk.handler.TaskExecutionHandler;
import com.ast.platform.workersdk.runtime.WorkerRuntimeManager;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

@RestController
@RequestMapping("/worker")
public class WorkerDispatchController {

    private static final Logger log = LoggerFactory.getLogger(WorkerDispatchController.class);

    private final Map<String, TaskExecutionHandler> handlerMap = new ConcurrentHashMap<>();
    private final ExecutorService executorService;
    private final WorkerControlPlaneClient controlPlaneClient;
    private final WorkerSdkProperties properties;
    private final WorkerRuntimeManager runtimeManager;
    private final Tracer tracer;

    public WorkerDispatchController(List<TaskExecutionHandler> handlers, 
                                    WorkerControlPlaneClient controlPlaneClient,
                                    WorkerSdkProperties properties,
                                    WorkerRuntimeManager runtimeManager,
                                    Tracer tracer) {
        this.controlPlaneClient = controlPlaneClient;
        this.properties = properties;
        this.runtimeManager = runtimeManager;
        this.tracer = tracer;
        this.executorService = Executors.newFixedThreadPool(properties.getMaxConcurrency());
        for (TaskExecutionHandler handler : handlers) {
            handlerMap.put(handler.taskType(), handler);
        }
    }

    @PostMapping("/dispatch")
    public WorkerDispatchResponse dispatch(@RequestBody WorkerDispatchRequest request) {
        // Micrometer Tracing automatically handles the HTTP trace context propagation
        log.info("Received dispatch request, taskId={}, taskType={}, token={}, traceId={}", 
                request.taskId(), request.taskType(), request.dispatchToken(), request.traceId());
        
        TaskExecutionHandler handler = handlerMap.get(request.taskType());
        if (handler == null) {
            log.warn("No handler found for taskType: {}", request.taskType());
            return WorkerDispatchResponse.fail("HANDLER_NOT_FOUND", "No handler registered for " + request.taskType());
        }

        // Try to acquire slot
        if (!runtimeManager.tryAcquireSlot()) {
            log.warn("No available slots for task: {}", request.taskId());
            return WorkerDispatchResponse.fail("NO_SLOTS", "Worker is at max capacity");
        }

        // Asynchronous execution to avoid blocking the scheduler
        executorService.submit(() -> {
            // Manually propagate span to the thread pool task
            Span span = tracer.nextSpan().name("execute-task").start();
            try (Tracer.SpanInScope ws = tracer.withSpan(span)) {
                // Ensure business traceId is also in MDC for log consistency
                MDC.put("traceId", request.traceId());
                log.info("Executing task: {}", request.taskId());
                
                ProgressReporter progressReporter = new ProgressReporter(request.taskId(), controlPlaneClient, properties);
                DefaultTaskContext context = new DefaultTaskContext(request.taskId(), request.payload(), progressReporter);
                
                handler.handle(context);
                
                // Report success
                controlPlaneClient.callbackResult(new WorkerCallbackRequest(
                        request.taskId(),
                        properties.getWorkerId(),
                        request.dispatchToken(),
                        request.traceId(),
                        TaskStatus.SUCCESS,
                        false,
                        "{\"status\":\"ok\"}",
                        null
                ));
                log.info("Task execution finished and reported: {}", request.taskId());
            } catch (Exception e) {
                log.error("Task execution error: {}", request.taskId(), e);
                // Report failure
                controlPlaneClient.callbackResult(new WorkerCallbackRequest(
                        request.taskId(),
                        properties.getWorkerId(),
                        request.dispatchToken(),
                        request.traceId(),
                        TaskStatus.FAILED,
                        true,
                        null,
                        e.getMessage()
                ));
            } finally {
                runtimeManager.releaseSlot();
                span.end();
                MDC.remove("traceId");
            }
        });

        return WorkerDispatchResponse.ok();
    }
}

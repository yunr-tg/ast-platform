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
import com.ast.platform.workersdk.lifecycle.WorkerGracefulShutdownHook;
import com.ast.platform.workersdk.runtime.WorkerRuntimeManager;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

@RestController
@RequestMapping("/worker")
public class WorkerDispatchController {

    private static final Logger log = LoggerFactory.getLogger(WorkerDispatchController.class);

    private final Map<String, TaskExecutionHandler> handlerMap = new ConcurrentHashMap<>();
    private final ExecutorService executorService;
    private final WorkerControlPlaneClient controlPlaneClient;
    private final WorkerSdkProperties properties;
    private final WorkerRuntimeManager runtimeManager;
    private final WorkerGracefulShutdownHook shutdownHook;
    private final Tracer tracer;

    public WorkerDispatchController(List<TaskExecutionHandler> handlers, 
                                    WorkerControlPlaneClient controlPlaneClient,
                                    WorkerSdkProperties properties,
                                    WorkerRuntimeManager runtimeManager,
                                    WorkerGracefulShutdownHook shutdownHook,
                                    Tracer tracer) {
        this.controlPlaneClient = controlPlaneClient;
        this.properties = properties;
        this.runtimeManager = runtimeManager;
        this.shutdownHook = shutdownHook;
        this.tracer = tracer;
        this.executorService = Executors.newFixedThreadPool(properties.getMaxConcurrency());
        for (TaskExecutionHandler handler : handlers) {
            handlerMap.put(handler.taskType(), handler);
        }
    }

    @PostMapping("/dispatch")
    public WorkerDispatchResponse dispatch(@RequestBody WorkerDispatchRequest request) {
        log.info("Received dispatch request, taskId={}, taskType={}, token={}, traceId={}", 
                request.taskId(), request.taskType(), request.dispatchToken(), request.traceId());
        
        if (shutdownHook.isShutdownTriggered() || runtimeManager.isDraining()) {
            log.warn("Worker is shutting down, rejecting task: {}", request.taskId());
            return WorkerDispatchResponse.fail("WORKER_DRAINING", "Worker is shutting down and not accepting new tasks");
        }
        
        TaskExecutionHandler handler = handlerMap.get(request.taskType());
        if (handler == null) {
            log.warn("No handler found for taskType: {}", request.taskType());
            return WorkerDispatchResponse.fail("HANDLER_NOT_FOUND", "No handler registered for " + request.taskType());
        }

        if (!runtimeManager.tryAcquireSlot(request.taskId())) {
            log.warn("No available slots for task: {} (draining={}, activeTasks={})", 
                    request.taskId(), runtimeManager.isDraining(), runtimeManager.getActiveTaskCount());
            return WorkerDispatchResponse.fail("NO_SLOTS", "Worker is at max capacity or draining");
        }

        executorService.submit(() -> {
            Span span = tracer.nextSpan().name("execute-task").start();
            try (Tracer.SpanInScope ws = tracer.withSpan(span)) {
                MDC.put("traceId", request.traceId());
                log.info("Executing task: {}", request.taskId());
                
                ProgressReporter progressReporter = new ProgressReporter(request.taskId(), controlPlaneClient, properties);
                DefaultTaskContext context = new DefaultTaskContext(request.taskId(), request.payload(), progressReporter);
                
                handler.handle(context);
                
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
                runtimeManager.releaseSlot(request.taskId());
                span.end();
                MDC.remove("traceId");
            }
        });

        return WorkerDispatchResponse.ok();
    }
    
    public void shutdown() {
        log.info("Shutting down dispatch controller executor");
        executorService.shutdown();
        try {
            if (!executorService.awaitTermination(properties.getGracefulShutdownTimeoutSeconds(), TimeUnit.SECONDS)) {
                log.warn("Executor did not terminate gracefully, forcing shutdown");
                executorService.shutdownNow();
            }
        } catch (InterruptedException e) {
            executorService.shutdownNow();
            Thread.currentThread().interrupt();
        }
    }
}

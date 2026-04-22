package com.ast.platform.workersdk.lifecycle;

import com.ast.platform.domain.worker.WorkerStatus;
import com.ast.platform.contract.worker.WorkerHeartbeatRequest;
import com.ast.platform.contract.worker.WorkerRegisterRequest;
import com.ast.platform.workersdk.client.WorkerControlPlaneClient;
import com.ast.platform.workersdk.config.WorkerSdkProperties;
import com.ast.platform.workersdk.handler.TaskExecutionHandler;
import com.ast.platform.workersdk.runtime.WorkerRuntimeManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.SmartLifecycle;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

@Component
public class WorkerLifecycleManager implements SmartLifecycle {

    private static final Logger log = LoggerFactory.getLogger(WorkerLifecycleManager.class);

    private final WorkerSdkProperties properties;
    private final WorkerControlPlaneClient client;
    private final WorkerRuntimeManager runtimeManager;
    private final List<TaskExecutionHandler> handlers;
    
    private final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
        Thread t = new Thread(r, "worker-heartbeat-thread");
        t.setDaemon(true);
        return t;
    });

    private volatile boolean running = false;

    public WorkerLifecycleManager(WorkerSdkProperties properties,
                                  WorkerControlPlaneClient client,
                                  WorkerRuntimeManager runtimeManager,
                                  List<TaskExecutionHandler> handlers) {
        this.properties = properties;
        this.client = client;
        this.runtimeManager = runtimeManager;
        this.handlers = handlers;
    }

    @Override
    public void start() {
        log.info("Starting Worker Lifecycle Manager...");
        try {
            register();
            startHeartbeat();
            running = true;
            log.info("Worker Lifecycle Manager started successfully, workerId={}", properties.getWorkerId());
        } catch (Exception e) {
            log.error("Failed to start Worker Lifecycle Manager", e);
            // In P0, we might want to fail the whole application if registration fails
        }
    }

    private void register() {
        List<String> taskTypes = handlers.stream().map(TaskExecutionHandler::taskType).toList();
        WorkerRegisterRequest request = new WorkerRegisterRequest(
                properties.getWorkerId(),
                properties.getWorkerGroup(),
                properties.getHost(),
                properties.getPort(),
                properties.getProtocol(),
                properties.getVersion(),
                taskTypes,
                List.of("default"), // P0 default tag
                properties.getMaxConcurrency(),
                100 // Default weight
        );
        client.registerWorker(request);
        log.info("Worker registered to control plane: {}", request);
    }

    private void startHeartbeat() {
        scheduler.scheduleWithFixedDelay(this::sendHeartbeat, 
                properties.getHeartbeatIntervalMs(), 
                properties.getHeartbeatIntervalMs(), 
                TimeUnit.MILLISECONDS);
    }

    private void sendHeartbeat() {
        try {
            List<String> taskTypes = handlers.stream().map(TaskExecutionHandler::taskType).toList();
            WorkerHeartbeatRequest request = new WorkerHeartbeatRequest(
                    properties.getWorkerId(),
                    properties.getWorkerGroup(),
                    taskTypes,
                    WorkerStatus.UP,
                    runtimeManager.getActiveTaskCount(),
                    runtimeManager.getMaxConcurrency(),
                    runtimeManager.getAvailableSlots(),
                    0, // avgRt P0
                    0, // errorRate P0
                    0.0, // cpuUsage P0
                    0.0  // memoryUsage P0
            );
            client.reportHeartbeat(request);
            log.debug("Heartbeat sent: {}", request);
        } catch (Exception e) {
            log.warn("Failed to send heartbeat: {}", e.getMessage());
        }
    }

    @Override
    public void stop() {
        log.info("Stopping Worker Lifecycle Manager...");
        scheduler.shutdown();
        try {
            if (!scheduler.awaitTermination(2, TimeUnit.SECONDS)) {
                scheduler.shutdownNow();
            }
            // Report DOWN status on shutdown
            List<String> taskTypes = handlers.stream().map(TaskExecutionHandler::taskType).toList();
            WorkerHeartbeatRequest request = new WorkerHeartbeatRequest(
                    properties.getWorkerId(),
                    properties.getWorkerGroup(),
                    taskTypes,
                    WorkerStatus.DOWN,
                    0,
                    runtimeManager.getMaxConcurrency(),
                    0,
                    0, 0,
                    0.0, 0.0
            );
            client.reportHeartbeat(request);
            log.info("Worker reported DOWN status on shutdown");
        } catch (Exception e) {
            log.warn("Error during worker shutdown report: {}", e.getMessage());
        }
        running = false;
    }

    @Override
    public boolean isRunning() {
        return running;
    }
}

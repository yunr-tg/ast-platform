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
import java.util.concurrent.atomic.AtomicBoolean;

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
    private final AtomicBoolean shutdownRequested = new AtomicBoolean(false);

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
                List.of("default"),
                properties.getMaxConcurrency(),
                100
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
            WorkerStatus status = determineStatus();
            
            WorkerHeartbeatRequest request = new WorkerHeartbeatRequest(
                    properties.getWorkerId(),
                    properties.getWorkerGroup(),
                    taskTypes,
                    status,
                    runtimeManager.getActiveTaskCount(),
                    runtimeManager.getMaxConcurrency(),
                    runtimeManager.getAvailableSlots(),
                    0,
                    0,
                    0.0,
                    0.0
            );
            client.reportHeartbeat(request);
            log.debug("Heartbeat sent: status={}, activeTasks={}", status, runtimeManager.getActiveTaskCount());
        } catch (Exception e) {
            log.warn("Failed to send heartbeat: {}", e.getMessage());
        }
    }

    private WorkerStatus determineStatus() {
        if (shutdownRequested.get() || runtimeManager.isDraining()) {
            return WorkerStatus.DRAINING;
        }
        return WorkerStatus.UP;
    }

    @Override
    public void stop() {
        log.info("Stopping Worker Lifecycle Manager...");
        
        if (properties.isGracefulShutdownEnabled()) {
            performGracefulShutdown();
        } else {
            performImmediateShutdown();
        }
        
        running = false;
        log.info("Worker Lifecycle Manager stopped");
    }

    private void performGracefulShutdown() {
        log.info("Initiating graceful shutdown, timeout={}s", properties.getGracefulShutdownTimeoutSeconds());
        
        shutdownRequested.set(true);
        runtimeManager.startDraining();
        
        reportStatus(WorkerStatus.DRAINING);
        
        try {
            boolean completed = runtimeManager.awaitDrainCompletion(
                    properties.getGracefulShutdownTimeoutSeconds(), 
                    TimeUnit.SECONDS);
            
            if (!completed) {
                log.warn("Graceful shutdown timeout, forcing completion");
                runtimeManager.forceComplete();
            }
        } catch (InterruptedException e) {
            log.warn("Graceful shutdown interrupted");
            Thread.currentThread().interrupt();
        }
        
        scheduler.shutdown();
        try {
            if (!scheduler.awaitTermination(2, TimeUnit.SECONDS)) {
                scheduler.shutdownNow();
            }
        } catch (InterruptedException e) {
            scheduler.shutdownNow();
            Thread.currentThread().interrupt();
        }
        
        reportStatus(WorkerStatus.DOWN);
        log.info("Graceful shutdown completed");
    }

    private void performImmediateShutdown() {
        log.info("Performing immediate shutdown");
        
        scheduler.shutdown();
        try {
            if (!scheduler.awaitTermination(2, TimeUnit.SECONDS)) {
                scheduler.shutdownNow();
            }
        } catch (InterruptedException e) {
            scheduler.shutdownNow();
            Thread.currentThread().interrupt();
        }
        
        reportStatus(WorkerStatus.DOWN);
    }

    private void reportStatus(WorkerStatus status) {
        try {
            List<String> taskTypes = handlers.stream().map(TaskExecutionHandler::taskType).toList();
            WorkerHeartbeatRequest request = new WorkerHeartbeatRequest(
                    properties.getWorkerId(),
                    properties.getWorkerGroup(),
                    taskTypes,
                    status,
                    runtimeManager.getActiveTaskCount(),
                    runtimeManager.getMaxConcurrency(),
                    runtimeManager.getAvailableSlots(),
                    0, 0,
                    0.0, 0.0
            );
            client.reportHeartbeat(request);
            log.info("Worker status reported: {} (activeTasks={})", status, runtimeManager.getActiveTaskCount());
        } catch (Exception e) {
            log.warn("Failed to report status {}: {}", status, e.getMessage());
        }
    }

    @Override
    public boolean isRunning() {
        return running;
    }
    
    @Override
    public int getPhase() {
        return Integer.MAX_VALUE;
    }
    
    @Override
    public void stop(Runnable callback) {
        stop();
        callback.run();
    }
}

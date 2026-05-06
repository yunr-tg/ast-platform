package com.ast.platform.workersdk.lifecycle;

import com.ast.platform.workersdk.config.WorkerSdkProperties;
import com.ast.platform.workersdk.runtime.WorkerRuntimeManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationListener;
import org.springframework.context.event.ContextClosedEvent;
import org.springframework.stereotype.Component;

import java.util.concurrent.atomic.AtomicBoolean;

@Component
public class WorkerGracefulShutdownHook implements ApplicationListener<ContextClosedEvent> {

    private static final Logger log = LoggerFactory.getLogger(WorkerGracefulShutdownHook.class);

    private final WorkerSdkProperties properties;
    private final WorkerRuntimeManager runtimeManager;
    private final AtomicBoolean shutdownTriggered = new AtomicBoolean(false);

    public WorkerGracefulShutdownHook(WorkerSdkProperties properties,
                                       WorkerRuntimeManager runtimeManager,
                                       ApplicationContext applicationContext) {
        this.properties = properties;
        this.runtimeManager = runtimeManager;
        
        registerShutdownHook();
        registerSignalHandler();
    }

    private void registerShutdownHook() {
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            if (shutdownTriggered.compareAndSet(false, true)) {
                log.info("JVM shutdown hook triggered");
                initiateGracefulShutdown();
            }
        }, "worker-shutdown-hook"));
        log.info("JVM shutdown hook registered");
    }

    private void registerSignalHandler() {
        try {
            sun.misc.Signal.handle(new sun.misc.Signal("TERM"), signal -> {
                log.info("Received SIGTERM signal, initiating graceful shutdown");
                if (shutdownTriggered.compareAndSet(false, true)) {
                    initiateGracefulShutdown();
                }
            });
            
            sun.misc.Signal.handle(new sun.misc.Signal("INT"), signal -> {
                log.info("Received SIGINT signal (Ctrl+C), initiating graceful shutdown");
                if (shutdownTriggered.compareAndSet(false, true)) {
                    initiateGracefulShutdown();
                }
            });
            
            log.info("Signal handlers registered for SIGTERM and SIGINT");
        } catch (Exception e) {
            log.warn("Failed to register signal handlers (may not be supported on this platform): {}", e.getMessage());
        }
    }

    private void initiateGracefulShutdown() {
        if (!properties.isGracefulShutdownEnabled()) {
            log.info("Graceful shutdown disabled, exiting immediately");
            return;
        }

        log.info("Initiating graceful shutdown process");
        log.info("Active tasks: {}, Max wait: {}s", 
                runtimeManager.getActiveTaskCount(),
                properties.getGracefulShutdownTimeoutSeconds());

        runtimeManager.startDraining();

        try {
            boolean completed = runtimeManager.awaitDrainCompletion(
                    properties.getGracefulShutdownTimeoutSeconds(),
                    java.util.concurrent.TimeUnit.SECONDS);

            if (completed) {
                log.info("All tasks completed gracefully");
            } else {
                log.warn("Graceful shutdown timeout, {} tasks may be interrupted",
                        runtimeManager.getActiveTaskCount());
            }
        } catch (InterruptedException e) {
            log.warn("Graceful shutdown interrupted");
            Thread.currentThread().interrupt();
        }
    }

    @Override
    public void onApplicationEvent(ContextClosedEvent event) {
        log.info("Spring context closed event received");
        if (shutdownTriggered.compareAndSet(false, true)) {
            initiateGracefulShutdown();
        }
    }

    public boolean isShutdownTriggered() {
        return shutdownTriggered.get();
    }
}

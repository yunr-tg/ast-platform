package com.ast.platform.workersdk.runtime;

import com.ast.platform.workersdk.config.WorkerSdkProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

@Component
public class WorkerRuntimeManager {

    private static final Logger log = LoggerFactory.getLogger(WorkerRuntimeManager.class);

    private final WorkerSdkProperties properties;
    private final AtomicInteger activeTaskCount = new AtomicInteger(0);
    private final Set<String> activeTaskIds = ConcurrentHashMap.newKeySet();
    private final AtomicBoolean draining = new AtomicBoolean(false);
    private volatile CountDownLatch drainLatch;

    public WorkerRuntimeManager(WorkerSdkProperties properties) {
        this.properties = properties;
    }

    public boolean tryAcquireSlot() {
        if (draining.get()) {
            log.debug("Worker is draining, rejecting new task");
            return false;
        }
        int current;
        do {
            current = activeTaskCount.get();
            if (current >= properties.getMaxConcurrency()) {
                return false;
            }
        } while (!activeTaskCount.compareAndSet(current, current + 1));
        return true;
    }

    public boolean tryAcquireSlot(String taskId) {
        if (draining.get()) {
            log.debug("Worker is draining, rejecting new task: {}", taskId);
            return false;
        }
        int current;
        do {
            current = activeTaskCount.get();
            if (current >= properties.getMaxConcurrency()) {
                return false;
            }
        } while (!activeTaskCount.compareAndSet(current, current + 1));
        activeTaskIds.add(taskId);
        log.debug("Task slot acquired: {}, activeTasks={}", taskId, activeTaskCount.get());
        return true;
    }

    public void releaseSlot() {
        int count = activeTaskCount.decrementAndGet();
        log.debug("Task slot released, activeTasks={}", count);
        if (draining.get() && count == 0 && drainLatch != null) {
            drainLatch.countDown();
        }
    }

    public void releaseSlot(String taskId) {
        activeTaskIds.remove(taskId);
        int count = activeTaskCount.decrementAndGet();
        log.debug("Task slot released: {}, activeTasks={}", taskId, count);
        if (draining.get() && count == 0 && drainLatch != null) {
            drainLatch.countDown();
        }
    }

    public int getActiveTaskCount() {
        return activeTaskCount.get();
    }

    public int getAvailableSlots() {
        return Math.max(0, properties.getMaxConcurrency() - activeTaskCount.get());
    }

    public int getMaxConcurrency() {
        return properties.getMaxConcurrency();
    }

    public Set<String> getActiveTaskIds() {
        return Set.copyOf(activeTaskIds);
    }

    public boolean isDraining() {
        return draining.get();
    }

    public void startDraining() {
        if (draining.compareAndSet(false, true)) {
            int currentTasks = activeTaskCount.get();
            drainLatch = new CountDownLatch(currentTasks);
            log.info("Worker entering DRAINING state, waiting for {} active tasks to complete", currentTasks);
        }
    }

    public boolean awaitDrainCompletion(long timeout, TimeUnit unit) throws InterruptedException {
        if (drainLatch == null) {
            return true;
        }
        int currentTasks = activeTaskCount.get();
        if (currentTasks == 0) {
            log.info("No active tasks, drain completed immediately");
            return true;
        }
        log.info("Waiting for {} active tasks to complete, timeout={}s", currentTasks, unit.toSeconds(timeout));
        boolean completed = drainLatch.await(timeout, unit);
        if (completed) {
            log.info("All active tasks completed, drain finished");
        } else {
            log.warn("Drain timeout reached, {} tasks still running", activeTaskCount.get());
        }
        return completed;
    }

    public void forceComplete() {
        if (drainLatch != null) {
            while (drainLatch.getCount() > 0) {
                drainLatch.countDown();
            }
        }
    }
}

package com.ast.platform.workersdk.runtime;

import com.ast.platform.workersdk.config.WorkerSdkProperties;
import org.springframework.stereotype.Component;

import java.util.concurrent.atomic.AtomicInteger;

@Component
public class WorkerRuntimeManager {

    private final WorkerSdkProperties properties;
    private final AtomicInteger activeTaskCount = new AtomicInteger(0);

    public WorkerRuntimeManager(WorkerSdkProperties properties) {
        this.properties = properties;
    }

    public boolean tryAcquireSlot() {
        int current;
        do {
            current = activeTaskCount.get();
            if (current >= properties.getMaxConcurrency()) {
                return false;
            }
        } while (!activeTaskCount.compareAndSet(current, current + 1));
        return true;
    }

    public void releaseSlot() {
        activeTaskCount.decrementAndGet();
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
}

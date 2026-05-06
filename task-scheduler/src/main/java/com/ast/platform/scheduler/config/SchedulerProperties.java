package com.ast.platform.scheduler.config;

import com.ast.platform.domain.task.SchedulingStrategy;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.cloud.context.config.annotation.RefreshScope;

import java.util.HashMap;
import java.util.Map;

@ConfigurationProperties(prefix = "ast.scheduler")
@RefreshScope
public class SchedulerProperties {

    private boolean localQueueEnabled = true;
    private boolean dispatcherEnabled;
    private boolean pumpEnabled;
    private long dispatcherFixedDelayMs = 1000L;
    private long retryFixedDelayMs = 1000L;
    private SchedulingStrategy strategy = SchedulingStrategy.LEAST_LOADED;
    private Map<String, SchedulingStrategy> typeStrategies = new HashMap<>();
    private WorkerDispatchProperties workerDispatch = new WorkerDispatchProperties();
    private CallbackProperties callback = new CallbackProperties();

    public boolean isLocalQueueEnabled() { return localQueueEnabled; }
    public void setLocalQueueEnabled(boolean localQueueEnabled) { this.localQueueEnabled = localQueueEnabled; }
    public boolean isDispatcherEnabled() { return dispatcherEnabled; }
    public void setDispatcherEnabled(boolean dispatcherEnabled) { this.dispatcherEnabled = dispatcherEnabled; }
    public boolean isPumpEnabled() { return pumpEnabled; }
    public void setPumpEnabled(boolean pumpEnabled) { this.pumpEnabled = pumpEnabled; }
    public long getDispatcherFixedDelayMs() { return dispatcherFixedDelayMs; }
    public void setDispatcherFixedDelayMs(long dispatcherFixedDelayMs) { this.dispatcherFixedDelayMs = dispatcherFixedDelayMs; }
    public long getRetryFixedDelayMs() { return retryFixedDelayMs; }
    public void setRetryFixedDelayMs(long retryFixedDelayMs) { this.retryFixedDelayMs = retryFixedDelayMs; }
    public SchedulingStrategy getStrategy() { return strategy; }
    public void setStrategy(SchedulingStrategy strategy) { this.strategy = strategy; }
    public Map<String, SchedulingStrategy> getTypeStrategies() { return typeStrategies; }
    public void setTypeStrategies(Map<String, SchedulingStrategy> typeStrategies) { this.typeStrategies = typeStrategies; }
    public WorkerDispatchProperties getWorkerDispatch() { return workerDispatch; }
    public void setWorkerDispatch(WorkerDispatchProperties workerDispatch) { this.workerDispatch = workerDispatch; }
    public CallbackProperties getCallback() { return callback; }
    public void setCallback(CallbackProperties callback) { this.callback = callback; }

    public static class WorkerDispatchProperties {
        private String type = "logging";
        private int timeoutMs = 5000;

        public String getType() { return type; }
        public void setType(String type) { this.type = type; }
        public int getTimeoutMs() { return timeoutMs; }
        public void setTimeoutMs(int timeoutMs) { this.timeoutMs = timeoutMs; }
    }

    public static class CallbackProperties {
        private boolean enabled = true;
        private int maxRetryCount = 3;

        public boolean isEnabled() { return enabled; }
        public void setEnabled(boolean enabled) { this.enabled = enabled; }
        public int getMaxRetryCount() { return maxRetryCount; }
        public void setMaxRetryCount(int maxRetryCount) { this.maxRetryCount = maxRetryCount; }
    }
}

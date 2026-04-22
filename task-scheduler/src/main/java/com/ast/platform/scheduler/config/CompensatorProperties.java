package com.ast.platform.scheduler.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "ast.scheduler.compensator")
public class CompensatorProperties {

    private boolean enabled;
    private long fixedDelayMs = 30000L;
    private long dispatchTimeoutMs = 300000L;
    private long executionTimeoutMs = 600000L;

    public boolean isEnabled() { return enabled; }
    public void setEnabled(boolean enabled) { this.enabled = enabled; }
    public long getFixedDelayMs() { return fixedDelayMs; }
    public void setFixedDelayMs(long fixedDelayMs) { this.fixedDelayMs = fixedDelayMs; }
    public long getDispatchTimeoutMs() { return dispatchTimeoutMs; }
    public void setDispatchTimeoutMs(long dispatchTimeoutMs) { this.dispatchTimeoutMs = dispatchTimeoutMs; }
    public long getExecutionTimeoutMs() { return executionTimeoutMs; }
    public void setExecutionTimeoutMs(long executionTimeoutMs) { this.executionTimeoutMs = executionTimeoutMs; }
}

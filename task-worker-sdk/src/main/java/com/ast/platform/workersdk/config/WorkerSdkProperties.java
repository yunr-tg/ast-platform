package com.ast.platform.workersdk.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "ast.worker-sdk")
public class WorkerSdkProperties {

    private String schedulerBaseUrl = "http://localhost:8081";
    private String workerId;
    private String workerGroup = "default-group";
    private String host;
    private int port = 8080;
    private String protocol = "http";
    private String version = "1.0.0";
    private int maxConcurrency = 10;
    private int heartbeatIntervalMs = 5000;

    public String getSchedulerBaseUrl() {
        return schedulerBaseUrl;
    }

    public void setSchedulerBaseUrl(String schedulerBaseUrl) {
        this.schedulerBaseUrl = schedulerBaseUrl;
    }

    public String getWorkerId() {
        return workerId;
    }

    public void setWorkerId(String workerId) {
        this.workerId = workerId;
    }

    public String getWorkerGroup() {
        return workerGroup;
    }

    public void setWorkerGroup(String workerGroup) {
        this.workerGroup = workerGroup;
    }

    public String getHost() {
        return host;
    }

    public void setHost(String host) {
        this.host = host;
    }

    public int getPort() {
        return port;
    }

    public void setPort(int port) {
        this.port = port;
    }

    public String getProtocol() {
        return protocol;
    }

    public void setProtocol(String protocol) {
        this.protocol = protocol;
    }

    public String getVersion() {
        return version;
    }

    public void setVersion(String version) {
        this.version = version;
    }

    public int getMaxConcurrency() {
        return maxConcurrency;
    }

    public void setMaxConcurrency(int maxConcurrency) {
        this.maxConcurrency = maxConcurrency;
    }

    public int getHeartbeatIntervalMs() {
        return heartbeatIntervalMs;
    }

    public void setHeartbeatIntervalMs(int heartbeatIntervalMs) {
        this.heartbeatIntervalMs = heartbeatIntervalMs;
    }
}

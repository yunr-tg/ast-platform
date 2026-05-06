package com.ast.platform.gateway.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.cloud.context.config.annotation.RefreshScope;

@ConfigurationProperties(prefix = "ast.gateway")
@RefreshScope
public class GatewayProperties {

    private String storageType = "jdbc";
    private String submitTopic = "ast.task.submit";
    private int outboxBatchSize = 100;
    private boolean immediatePublishEnabled = true;
    private boolean mockSendSuccess = true;
    private long outboxRepublishFixedDelayMs = 3000L;
    private long outboxBaseRetryDelayMs = 5000L;

    public String getStorageType() {
        return storageType;
    }

    public void setStorageType(String storageType) {
        this.storageType = storageType;
    }

    public String getSubmitTopic() {
        return submitTopic;
    }

    public void setSubmitTopic(String submitTopic) {
        this.submitTopic = submitTopic;
    }

    public int getOutboxBatchSize() {
        return outboxBatchSize;
    }

    public void setOutboxBatchSize(int outboxBatchSize) {
        this.outboxBatchSize = outboxBatchSize;
    }

    public boolean isImmediatePublishEnabled() {
        return immediatePublishEnabled;
    }

    public void setImmediatePublishEnabled(boolean immediatePublishEnabled) {
        this.immediatePublishEnabled = immediatePublishEnabled;
    }

    public boolean isMockSendSuccess() {
        return mockSendSuccess;
    }

    public void setMockSendSuccess(boolean mockSendSuccess) {
        this.mockSendSuccess = mockSendSuccess;
    }

    public long getOutboxRepublishFixedDelayMs() {
        return outboxRepublishFixedDelayMs;
    }

    public void setOutboxRepublishFixedDelayMs(long outboxRepublishFixedDelayMs) {
        this.outboxRepublishFixedDelayMs = outboxRepublishFixedDelayMs;
    }

    public long getOutboxBaseRetryDelayMs() {
        return outboxBaseRetryDelayMs;
    }

    public void setOutboxBaseRetryDelayMs(long outboxBaseRetryDelayMs) {
        this.outboxBaseRetryDelayMs = outboxBaseRetryDelayMs;
    }
}

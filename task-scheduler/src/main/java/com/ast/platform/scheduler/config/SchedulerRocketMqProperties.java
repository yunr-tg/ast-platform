package com.ast.platform.scheduler.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix = "ast.scheduler.rocketmq")
public class SchedulerRocketMqProperties {

    private boolean enabled = false;
    private String endpoints = "127.0.0.1:8081";
    private String consumerGroup = "GID_AST_PLATFORM_PUMP";
    private String topic = "AST_TASK_SUBMIT_TOPIC";
    private String tag = "*";
    private int maxCacheMessages = 1000;
    private int consumptionThreadCount = 20;

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public String getEndpoints() {
        return endpoints;
    }

    public void setEndpoints(String endpoints) {
        this.endpoints = endpoints;
    }

    public String getConsumerGroup() {
        return consumerGroup;
    }

    public void setConsumerGroup(String consumerGroup) {
        this.consumerGroup = consumerGroup;
    }

    public String getTopic() {
        return topic;
    }

    public void setTopic(String topic) {
        this.topic = topic;
    }

    public String getTag() {
        return tag;
    }

    public void setTag(String tag) {
        this.tag = tag;
    }

    public int getMaxCacheMessages() {
        return maxCacheMessages;
    }

    public void setMaxCacheMessages(int maxCacheMessages) {
        this.maxCacheMessages = maxCacheMessages;
    }

    public int getConsumptionThreadCount() {
        return consumptionThreadCount;
    }

    public void setConsumptionThreadCount(int consumptionThreadCount) {
        this.consumptionThreadCount = consumptionThreadCount;
    }
}

package com.ast.platform.gateway.infrastructure.mq;

import com.ast.platform.gateway.config.GatewayProperties;
import com.ast.platform.gateway.domain.gateway.TaskMessagePublisher;
import com.ast.platform.gateway.domain.model.TaskPublishOutbox;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(prefix = "ast.gateway.rocketmq", name = "enabled", havingValue = "false", matchIfMissing = true)
public class LoggingTaskMessagePublisher implements TaskMessagePublisher {

    private static final Logger log = LoggerFactory.getLogger(LoggingTaskMessagePublisher.class);

    private final GatewayProperties properties;

    public LoggingTaskMessagePublisher(GatewayProperties properties) {
        this.properties = properties;
    }

    @Override
    public boolean publish(TaskPublishOutbox outbox) {
        boolean success = properties.isMockSendSuccess();
        log.info("publish gateway outbox, outboxId={}, taskId={}, topic={}, success={}",
                outbox.outboxId(), outbox.taskId(), outbox.topic(), success);
        return success;
    }
}

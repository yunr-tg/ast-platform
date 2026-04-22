package com.ast.platform.gateway.infrastructure.mq;

import com.ast.platform.gateway.config.GatewayProperties;
import com.ast.platform.gateway.config.GatewayRocketMqProperties;
import com.ast.platform.gateway.domain.gateway.TaskMessagePublisher;
import com.ast.platform.gateway.domain.model.TaskPublishOutbox;
import org.apache.rocketmq.client.apis.ClientException;
import org.apache.rocketmq.client.apis.ClientServiceProvider;
import org.apache.rocketmq.client.apis.message.Message;
import org.apache.rocketmq.client.apis.producer.Producer;
import org.apache.rocketmq.client.apis.producer.SendReceipt;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;

@Component
@ConditionalOnProperty(prefix = "ast.gateway.rocketmq", name = "enabled", havingValue = "true")
public class RocketMqGrpcTaskMessagePublisher implements TaskMessagePublisher {

    private static final Logger log = LoggerFactory.getLogger(RocketMqGrpcTaskMessagePublisher.class);

    private final ClientServiceProvider clientServiceProvider;
    private final Producer producer;
    private final GatewayProperties gatewayProperties;
    private final GatewayRocketMqProperties rocketMqProperties;

    public RocketMqGrpcTaskMessagePublisher(ClientServiceProvider clientServiceProvider,
                                            Producer producer,
                                            GatewayProperties gatewayProperties,
                                            GatewayRocketMqProperties rocketMqProperties) {
        this.clientServiceProvider = clientServiceProvider;
        this.producer = producer;
        this.gatewayProperties = gatewayProperties;
        this.rocketMqProperties = rocketMqProperties;
    }

    @Override
    public boolean publish(TaskPublishOutbox outbox) {
        Message message = clientServiceProvider.newMessageBuilder()
                .setTopic(gatewayProperties.getSubmitTopic())
                .setKeys(outbox.taskId())
                .setTag(rocketMqProperties.getMessageTag())
                .setBody(outbox.payload().getBytes(StandardCharsets.UTF_8))
                .build();
        try {
            SendReceipt sendReceipt = producer.send(message);
            log.info("publish gateway outbox by rocketmq grpc, outboxId={}, taskId={}, topic={}, messageId={}",
                    outbox.outboxId(), outbox.taskId(), outbox.topic(), sendReceipt.getMessageId());
            return true;
        } catch (ClientException ex) {
            log.error("publish gateway outbox by rocketmq grpc failed, outboxId={}, taskId={}, topic={}",
                    outbox.outboxId(), outbox.taskId(), outbox.topic(), ex);
            return false;
        }
    }
}

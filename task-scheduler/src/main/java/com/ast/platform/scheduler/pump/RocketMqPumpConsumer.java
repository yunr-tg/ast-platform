package com.ast.platform.scheduler.pump;

import com.ast.platform.scheduler.config.SchedulerRocketMqProperties;
import com.ast.platform.scheduler.pump.application.PumpIngestApplicationService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.rocketmq.client.apis.ClientConfiguration;
import org.apache.rocketmq.client.apis.ClientServiceProvider;
import org.apache.rocketmq.client.apis.consumer.ConsumeResult;
import org.apache.rocketmq.client.apis.consumer.FilterExpression;
import org.apache.rocketmq.client.apis.consumer.FilterExpressionType;
import org.apache.rocketmq.client.apis.consumer.PushConsumer;
import org.apache.rocketmq.client.apis.message.MessageView;
import io.micrometer.tracing.Span;
import io.micrometer.tracing.Tracer;
import org.slf4j.MDC;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.SmartLifecycle;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.util.Collections;

@Component
@ConditionalOnProperty(prefix = "ast.scheduler.rocketmq", name = "enabled", havingValue = "true")
public class RocketMqPumpConsumer implements SmartLifecycle {

    private static final Logger log = LoggerFactory.getLogger(RocketMqPumpConsumer.class);

    private final SchedulerRocketMqProperties rocketMqProperties;
    private final PumpIngestApplicationService pumpIngestApplicationService;
    private final ObjectMapper objectMapper;
    private final Tracer tracer;
    private PushConsumer pushConsumer;
    private volatile boolean running = false;

    public RocketMqPumpConsumer(SchedulerRocketMqProperties rocketMqProperties,
                                PumpIngestApplicationService pumpIngestApplicationService,
                                ObjectMapper objectMapper,
                                Tracer tracer) {
        this.rocketMqProperties = rocketMqProperties;
        this.pumpIngestApplicationService = pumpIngestApplicationService;
        this.objectMapper = objectMapper;
        this.tracer = tracer;
    }

    @Override
    public void start() {
        log.info("Starting RocketMQ Pump Consumer, endpoints={}, topic={}", 
                rocketMqProperties.getEndpoints(), rocketMqProperties.getTopic());
        
        final ClientServiceProvider provider = ClientServiceProvider.loadService();
        ClientConfiguration clientConfiguration = ClientConfiguration.newBuilder()
                .setEndpoints(rocketMqProperties.getEndpoints())
                .build();

        FilterExpression filterExpression = new FilterExpression(rocketMqProperties.getTag(), FilterExpressionType.TAG);
        
        try {
            pushConsumer = provider.newPushConsumerBuilder()
                    .setClientConfiguration(clientConfiguration)
                    .setConsumerGroup(rocketMqProperties.getConsumerGroup())
                    .setSubscriptionExpressions(Collections.singletonMap(rocketMqProperties.getTopic(), filterExpression))
                    .setMaxCacheMessageCount(rocketMqProperties.getMaxCacheMessages())
                    .setConsumptionThreadCount(rocketMqProperties.getConsumptionThreadCount())
                    .setMessageListener(this::consume)
                    .build();
            
            running = true;
            log.info("RocketMQ Pump Consumer started successfully");
        } catch (Exception e) {
            log.error("Failed to start RocketMQ Pump Consumer", e);
            throw new RuntimeException("RocketMQ Consumer start failure", e);
        }
    }

    private ConsumeResult consume(MessageView messageView) {
        String body = StandardCharsets.UTF_8.decode(messageView.getBody()).toString();
        log.debug("Received message from RocketMQ: {}", body);
        
        Span span = tracer.nextSpan().name("consume-task").start();
        try (Tracer.SpanInScope ws = tracer.withSpan(span)) {
            JsonNode node = objectMapper.readTree(body);
            String taskId = node.path("taskId").asText();
            String tenantId = node.path("tenantId").asText();
            String taskType = node.path("taskType").asText();
            String workerGroup = node.path("workerGroup").asText();
            String traceId = node.path("traceId").asText();
            int priority = node.has("priority") ? node.path("priority").asInt(5) : 5;

            // Link business traceId to MDC
            MDC.put("traceId", traceId);

            if (taskId.isEmpty() || tenantId.isEmpty()) {
                log.warn("Received invalid message body, taskId or tenantId is empty: {}", body);
                return ConsumeResult.SUCCESS; // Skip invalid message
            }

            pumpIngestApplicationService.acceptSubmittedTask(taskId, tenantId, taskType, workerGroup, traceId, priority);
            return ConsumeResult.SUCCESS;
        } catch (Exception e) {
            log.error("Failed to process message from RocketMQ: {}", body, e);
            return ConsumeResult.FAILURE; // Retry
        } finally {
            span.end();
            MDC.remove("traceId");
        }
    }

    @Override
    public void stop() {
        if (pushConsumer != null) {
            try {
                pushConsumer.close();
                log.info("RocketMQ Pump Consumer closed");
            } catch (Exception e) {
                log.error("Error closing RocketMQ Pump Consumer", e);
            }
        }
        running = false;
    }

    @Override
    public boolean isRunning() {
        return running;
    }
}

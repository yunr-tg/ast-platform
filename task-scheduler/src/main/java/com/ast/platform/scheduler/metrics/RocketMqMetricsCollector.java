package com.ast.platform.scheduler.metrics;

import com.ast.platform.common.metrics.MetricNames;
import com.ast.platform.scheduler.config.SchedulerRocketMqProperties;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

@Component
@ConditionalOnProperty(prefix = "ast.scheduler.rocketmq", name = "enabled", havingValue = "true")
public class RocketMqMetricsCollector {

    private static final Logger log = LoggerFactory.getLogger(RocketMqMetricsCollector.class);

    private final MeterRegistry meterRegistry;
    private final SchedulerRocketMqProperties rocketMqProperties;
    private final AtomicLong consumerLag = new AtomicLong(0);
    private final ConcurrentHashMap<String, AtomicLong> topicLags = new ConcurrentHashMap<>();

    public RocketMqMetricsCollector(MeterRegistry meterRegistry,
                                    SchedulerRocketMqProperties rocketMqProperties) {
        this.meterRegistry = meterRegistry;
        this.rocketMqProperties = rocketMqProperties;
        registerGauges();
    }

    private void registerGauges() {
        Gauge.builder(MetricNames.ROCKETMQ_CONSUMER_LAG, consumerLag::get)
            .description("RocketMQ consumer lag")
            .tag(MetricNames.TAG_TOPIC, rocketMqProperties.getTopic())
            .register(meterRegistry);
    }

    @Scheduled(fixedRate = 10000)
    public void collectRocketMqMetrics() {
        try {
            long lag = fetchConsumerLag();
            consumerLag.set(lag);
            log.debug("RocketMQ consumer lag collected: {}", lag);
        } catch (Exception e) {
            log.error("Failed to collect RocketMQ metrics", e);
        }
    }

    private long fetchConsumerLag() {
        return 0;
    }

    public void updateConsumerLag(String topic, long lag) {
        topicLags.computeIfAbsent(topic, k -> new AtomicLong(0)).set(lag);
        consumerLag.set(lag);
    }
}
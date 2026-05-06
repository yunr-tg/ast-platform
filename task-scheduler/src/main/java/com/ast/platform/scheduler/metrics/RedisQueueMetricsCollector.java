package com.ast.platform.scheduler.metrics;

import com.ast.platform.common.metrics.MetricNames;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class RedisQueueMetricsCollector {

    private static final Logger log = LoggerFactory.getLogger(RedisQueueMetricsCollector.class);
    private static final String READY_QUEUE_PATTERN = "dispatch:ready:*";
    private static final String PROCESSING_QUEUE_PATTERN = "dispatch:processing:*";
    private static final String RETRY_QUEUE_PATTERN = "dispatch:retry:*";

    private final RedisTemplate<String, String> redisTemplate;
    private final MeterRegistry meterRegistry;
    private final ConcurrentHashMap<String, Double> queueSizes = new ConcurrentHashMap<>();

    public RedisQueueMetricsCollector(RedisTemplate<String, String> redisTemplate,
                                      MeterRegistry meterRegistry) {
        this.redisTemplate = redisTemplate;
        this.meterRegistry = meterRegistry;
        registerGauges();
    }

    private void registerGauges() {
        Gauge.builder(MetricNames.REDIS_QUEUE_READY_SIZE, this::getReadyQueueSize)
            .description("Redis ready queue total size")
            .register(meterRegistry);

        Gauge.builder(MetricNames.REDIS_QUEUE_PROCESSING_SIZE, this::getProcessingQueueSize)
            .description("Redis processing queue total size")
            .register(meterRegistry);

        Gauge.builder(MetricNames.REDIS_QUEUE_RETRY_SIZE, this::getRetryQueueSize)
            .description("Redis retry queue total size")
            .register(meterRegistry);
    }

    @Scheduled(fixedRate = 5000)
    public void collectQueueMetrics() {
        try {
            long readySize = calculateQueueSize(READY_QUEUE_PATTERN);
            long processingSize = calculateQueueSize(PROCESSING_QUEUE_PATTERN);
            long retrySize = calculateQueueSize(RETRY_QUEUE_PATTERN);

            queueSizes.put("ready", (double) readySize);
            queueSizes.put("processing", (double) processingSize);
            queueSizes.put("retry", (double) retrySize);

            log.debug("Redis queue metrics collected: ready={}, processing={}, retry={}", 
                readySize, processingSize, retrySize);
        } catch (Exception e) {
            log.error("Failed to collect Redis queue metrics", e);
        }
    }

    private long calculateQueueSize(String pattern) {
        Set<String> keys = redisTemplate.keys(pattern);
        if (keys == null || keys.isEmpty()) {
            return 0;
        }
        long totalSize = 0;
        for (String key : keys) {
            Long size = redisTemplate.opsForList().size(key);
            if (size != null) {
                totalSize += size;
            }
        }
        return totalSize;
    }

    private double getReadyQueueSize() {
        return queueSizes.getOrDefault("ready", 0.0);
    }

    private double getProcessingQueueSize() {
        return queueSizes.getOrDefault("processing", 0.0);
    }

    private double getRetryQueueSize() {
        return queueSizes.getOrDefault("retry", 0.0);
    }
}
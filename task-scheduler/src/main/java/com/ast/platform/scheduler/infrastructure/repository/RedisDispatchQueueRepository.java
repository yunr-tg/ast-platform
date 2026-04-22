package com.ast.platform.scheduler.infrastructure.repository;

import com.ast.platform.infra.redis.DispatchRedisKeys;
import com.ast.platform.scheduler.domain.model.ReadyTaskEnvelope;
import com.ast.platform.scheduler.domain.repository.DispatchQueueRepository;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.Set;

@Repository
@ConditionalOnProperty(prefix = "ast.scheduler", name = "local-queue-enabled", havingValue = "false")
public class RedisDispatchQueueRepository implements DispatchQueueRepository {

    private static final Logger log = LoggerFactory.getLogger(RedisDispatchQueueRepository.class);

    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;
    private final RedisScript<List> dispatchScript;

    public RedisDispatchQueueRepository(StringRedisTemplate redisTemplate, ObjectMapper objectMapper) {
        this.redisTemplate = redisTemplate;
        this.objectMapper = objectMapper;
        this.dispatchScript = RedisScript.of(new ClassPathResource("lua/dispatch_task.lua"), List.class);
    }

    @Override
    public void enqueueReady(ReadyTaskEnvelope envelope) {
        String queueKey = DispatchRedisKeys.readyQueue(envelope.tenantId(), envelope.taskType());
        String activeKey = envelope.tenantId() + ":" + envelope.taskType();
        try {
            String json = objectMapper.writeValueAsString(envelope);
            redisTemplate.opsForList().rightPush(queueKey, json);
            
            // Maintain active keys for round-robin
            // Use a Set for deduplication and a List for rotation
            Long added = redisTemplate.opsForSet().add(DispatchRedisKeys.activeKeys() + ":set", activeKey);
            if (added != null && added > 0) {
                redisTemplate.opsForList().leftPush(DispatchRedisKeys.activeKeys(), activeKey);
            }
        } catch (JsonProcessingException e) {
            log.error("Failed to serialize ReadyTaskEnvelope, taskId={}", envelope.taskId(), e);
            throw new RuntimeException("Serialization error", e);
        }
    }

    @Override
    @SuppressWarnings("unchecked")
    public Optional<ReadyTaskEnvelope> pollNextReadyTask() {
        List<String> result = redisTemplate.execute(
                dispatchScript,
                List.of(DispatchRedisKeys.activeKeys(), DispatchRedisKeys.activeKeys() + ":set"),
                "dispatch:ready:",
                "dispatch:processing:",
                String.valueOf(System.currentTimeMillis())
        );

        if (result == null || result.isEmpty()) {
            return Optional.empty();
        }

        // result[0] is activeKey (tenantId:taskType), result[1] is taskJson
        String json = result.get(1);
        try {
            return Optional.of(objectMapper.readValue(json, ReadyTaskEnvelope.class));
        } catch (JsonProcessingException e) {
            log.error("Failed to deserialize ReadyTaskEnvelope from Redis result", e);
            return Optional.empty();
        }
    }

    @Override
    public void commitTask(ReadyTaskEnvelope envelope) {
        String processingQueue = DispatchRedisKeys.processingQueue(envelope.tenantId(), envelope.taskType());
        try {
            String json = objectMapper.writeValueAsString(envelope);
            redisTemplate.opsForZSet().remove(processingQueue, json);
        } catch (JsonProcessingException e) {
            log.error("Failed to serialize ReadyTaskEnvelope for commit, taskId={}", envelope.taskId(), e);
        }
    }

    @Override
    public void rollbackTask(ReadyTaskEnvelope envelope) {
        String processingQueue = DispatchRedisKeys.processingQueue(envelope.tenantId(), envelope.taskType());
        String readyQueue = DispatchRedisKeys.readyQueue(envelope.tenantId(), envelope.taskType());
        try {
            String json = objectMapper.writeValueAsString(envelope);
            // Move back from processing ZSet to ready List
            redisTemplate.opsForZSet().remove(processingQueue, json);
            redisTemplate.opsForList().leftPush(readyQueue, json);
            
            // Ensure it's back in active keys
            String activeKey = envelope.tenantId() + ":" + envelope.taskType();
            Long added = redisTemplate.opsForSet().add(DispatchRedisKeys.activeKeys() + ":set", activeKey);
            if (added != null && added > 0) {
                redisTemplate.opsForList().leftPush(DispatchRedisKeys.activeKeys(), activeKey);
            }
        } catch (JsonProcessingException e) {
            log.error("Failed to serialize ReadyTaskEnvelope for rollback, taskId={}", envelope.taskId(), e);
        }
    }

    @Override
    public void enqueueRetry(ReadyTaskEnvelope envelope, Instant dueTime) {
        String retryQueueKey = DispatchRedisKeys.retryQueue(envelope.tenantId(), envelope.taskType());
        try {
            String json = objectMapper.writeValueAsString(envelope);
            redisTemplate.opsForZSet().add(retryQueueKey, json, dueTime.toEpochMilli());
            redisTemplate.opsForSet().add("dispatch:retry:active:keys", envelope.tenantId() + ":" + envelope.taskType());
        } catch (JsonProcessingException e) {
            log.error("Failed to serialize ReadyTaskEnvelope for retry, taskId={}", envelope.taskId(), e);
        }
    }

    @Override
    public int moveDueRetryTasks(Instant now) {
        Set<String> retryActiveKeys = redisTemplate.opsForSet().members("dispatch:retry:active:keys");
        if (retryActiveKeys == null || retryActiveKeys.isEmpty()) {
            return 0;
        }

        int movedTotal = 0;
        for (String activeKey : retryActiveKeys) {
            String[] parts = activeKey.split(":");
            if (parts.length != 2) continue;

            String tenantId = parts[0];
            String taskType = parts[1];
            String retryQueueKey = DispatchRedisKeys.retryQueue(tenantId, taskType);

            Set<String> dueTasks = redisTemplate.opsForZSet().rangeByScore(retryQueueKey, 0, now.toEpochMilli());
            if (dueTasks != null && !dueTasks.isEmpty()) {
                for (String json : dueTasks) {
                    try {
                        ReadyTaskEnvelope envelope = objectMapper.readValue(json, ReadyTaskEnvelope.class);
                        enqueueReady(envelope);
                        redisTemplate.opsForZSet().remove(retryQueueKey, json);
                        movedTotal++;
                    } catch (JsonProcessingException e) {
                        log.error("Failed to deserialize retry task, key={}", retryQueueKey, e);
                    }
                }
            }
        }
        return movedTotal;
    }

    @Override
    public int reclaimTimeoutTasks(Instant timeoutThreshold) {
        Set<String> processingActiveKeys = redisTemplate.opsForSet().members("dispatch:processing:active:keys");
        if (processingActiveKeys == null || processingActiveKeys.isEmpty()) {
            return 0;
        }

        int reclaimedTotal = 0;
        for (String activeKey : processingActiveKeys) {
            String[] parts = activeKey.split(":");
            if (parts.length != 2) continue;

            String tenantId = parts[0];
            String taskType = parts[1];
            String processingQueue = DispatchRedisKeys.processingQueue(tenantId, taskType);

            // Find tasks that have been in processing for too long
            Set<String> timeoutTasks = redisTemplate.opsForZSet().rangeByScore(processingQueue, 0, timeoutThreshold.toEpochMilli());
            if (timeoutTasks != null && !timeoutTasks.isEmpty()) {
                for (String json : timeoutTasks) {
                    try {
                        ReadyTaskEnvelope envelope = objectMapper.readValue(json, ReadyTaskEnvelope.class);
                        log.warn("Reclaiming timeout task, taskId={}, tenantId={}, taskType={}", 
                                envelope.taskId(), tenantId, taskType);
                        
                        // Move back to ready queue
                        rollbackTask(envelope);
                        reclaimedTotal++;
                    } catch (JsonProcessingException e) {
                        log.error("Failed to deserialize timeout task, key={}", processingQueue, e);
                    }
                }
            } else {
                // Optional: remove activeKey from processing:active:keys if queue is empty
                Long size = redisTemplate.opsForZSet().size(processingQueue);
                if (size != null && size == 0) {
                    redisTemplate.opsForSet().remove("dispatch:processing:active:keys", activeKey);
                }
            }
        }
        return reclaimedTotal;
    }
}

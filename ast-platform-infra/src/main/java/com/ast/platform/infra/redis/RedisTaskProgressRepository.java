package com.ast.platform.infra.redis;

import com.ast.platform.domain.task.TaskProgress;
import com.ast.platform.domain.task.TaskProgressRepository;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Repository;

import java.time.Duration;
import java.util.Map;
import java.util.Optional;

@Repository
public class RedisTaskProgressRepository implements TaskProgressRepository {

    private static final Logger log = LoggerFactory.getLogger(RedisTaskProgressRepository.class);

    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;

    private static final String SNAPSHOT_KEY_PREFIX = "ats:task:progress:snapshot:";
    private static final String TIMELINE_KEY_PREFIX = "ats:task:progress:timeline:";
    private static final Duration EXPIRATION = Duration.ofHours(24);
    private static final int MAX_TIMELINE_SIZE = 50;

    public RedisTaskProgressRepository(StringRedisTemplate redisTemplate, ObjectMapper objectMapper) {
        this.redisTemplate = redisTemplate;
        this.objectMapper = objectMapper;
    }

    @Override
    public void saveProgress(TaskProgress progress, long sequence) {
        String snapshotKey = SNAPSHOT_KEY_PREFIX + progress.taskId();
        String timelineKey = TIMELINE_KEY_PREFIX + progress.taskId();

        try {
            // 1. Check sequence to avoid out-of-order updates
            String lastSeqStr = (String) redisTemplate.opsForHash().get(snapshotKey, "s");
            if (lastSeqStr != null && Long.parseLong(lastSeqStr) >= sequence) {
                log.debug("Out of order progress report for task {}, ignoring. seq={}", progress.taskId(), sequence);
                return;
            }

            // 2. Update L1 Snapshot
            String payloadJson = progress.payload() != null ? objectMapper.writeValueAsString(progress.payload()) : null;
            
            Map<String, String> hash = new java.util.HashMap<>(Map.of(
                    "p", String.valueOf(progress.percentage()),
                    "m", progress.message() != null ? progress.message() : "",
                    "s", String.valueOf(sequence),
                    "t", String.valueOf(progress.timestamp())
            ));
            if (payloadJson != null) {
                hash.put("d", payloadJson);
            }
            redisTemplate.opsForHash().putAll(snapshotKey, hash);
            redisTemplate.expire(snapshotKey, EXPIRATION);

            // 3. Update L2 Timeline (Sampled)
            String timelineEntry = objectMapper.writeValueAsString(progress);
            redisTemplate.opsForList().rightPush(timelineKey, timelineEntry);
            redisTemplate.opsForList().trim(timelineKey, -MAX_TIMELINE_SIZE, -1);
            redisTemplate.expire(timelineKey, EXPIRATION);

        } catch (JsonProcessingException e) {
            log.error("Failed to serialize progress report for task {}", progress.taskId(), e);
        }
    }

    @Override
    public Optional<TaskProgress> getLatestProgress(String taskId) {
        String snapshotKey = SNAPSHOT_KEY_PREFIX + taskId;
        Map<Object, Object> hash = redisTemplate.opsForHash().entries(snapshotKey);
        
        if (hash.isEmpty()) {
            return Optional.empty();
        }

        try {
            int percentage = Integer.parseInt((String) hash.get("p"));
            String message = (String) hash.get("m");
            long timestamp = Long.parseLong((String) hash.get("t"));
            String payloadJson = (String) hash.get("d");
            Map<String, Object> payload = null;
            if (payloadJson != null && !payloadJson.isEmpty()) {
                payload = objectMapper.readValue(payloadJson, new TypeReference<Map<String, Object>>() {});
            }

            return Optional.of(new TaskProgress(taskId, percentage, message, payload, timestamp));
        } catch (Exception e) {
            log.error("Failed to deserialize progress snapshot for task {}", taskId, e);
            return Optional.empty();
        }
    }
}

package com.ast.platform.scheduler.infrastructure.selector;

import com.ast.platform.scheduler.domain.model.ReadyTaskEnvelope;
import com.ast.platform.scheduler.domain.model.WorkerRuntimeSnapshot;
import com.ast.platform.scheduler.domain.service.WorkerSelector;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;
import java.util.List;
import java.util.Optional;

/**
 * Smooth Weighted Round Robin (Nginx style)
 * State is stored in Redis to ensure consistency across scheduler nodes.
 */
@Component
public class WeightedRoundRobinWorkerSelector implements WorkerSelector {

    private final StringRedisTemplate redisTemplate;
    private static final String REDIS_KEY_PREFIX = "ats:scheduler:wrr:current:";

    public WeightedRoundRobinWorkerSelector(StringRedisTemplate redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    @Override
    public Optional<WorkerRuntimeSnapshot> select(ReadyTaskEnvelope task, List<WorkerRuntimeSnapshot> candidates) {
        if (candidates == null || candidates.isEmpty()) {
            return Optional.empty();
        }

        if (candidates.size() == 1) {
            return Optional.of(candidates.get(0));
        }

        int totalWeight = 0;
        WorkerRuntimeSnapshot best = null;
        double maxCurrentWeight = Double.NEGATIVE_INFINITY;

        for (WorkerRuntimeSnapshot worker : candidates) {
            int weight = worker.weight();
            totalWeight += weight;

            String redisKey = REDIS_KEY_PREFIX + task.workerGroup() + ":" + worker.workerId();
            // In a real production system, we'd use a Lua script for atomicity.
            // For P0, we'll do a simple increment and get.
            Double currentWeight = redisTemplate.opsForValue().increment(redisKey, (double) weight);
            if (currentWeight == null) currentWeight = (double) weight;

            if (currentWeight > maxCurrentWeight) {
                maxCurrentWeight = currentWeight;
                best = worker;
            }
        }

        if (best != null) {
            String bestKey = REDIS_KEY_PREFIX + task.workerGroup() + ":" + best.workerId();
            redisTemplate.opsForValue().increment(bestKey, (double) -totalWeight);
            return Optional.of(best);
        }

        return Optional.of(candidates.get(0));
    }
}

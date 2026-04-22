package com.ast.platform.scheduler.infrastructure.selector;

import com.ast.platform.scheduler.domain.model.ReadyTaskEnvelope;
import com.ast.platform.scheduler.domain.model.WorkerRuntimeSnapshot;
import com.ast.platform.scheduler.domain.service.WorkerSelector;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;
import java.util.List;
import java.util.Optional;

/**
 * Deficit Round Robin (DRR) Worker Selector
 * Aims for tenant fairness on workers.
 * Each tenant has a 'deficit' (usage) on each worker.
 * We pick the worker where the tenant has the most remaining 'credit'.
 */
@Component
public class DeficitRoundRobinWorkerSelector implements WorkerSelector {

    private final StringRedisTemplate redisTemplate;
    private static final String REDIS_KEY_PREFIX = "ats:scheduler:drr:usage:";

    public DeficitRoundRobinWorkerSelector(StringRedisTemplate redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    @Override
    public Optional<WorkerRuntimeSnapshot> select(ReadyTaskEnvelope task, List<WorkerRuntimeSnapshot> candidates) {
        if (candidates == null || candidates.isEmpty()) {
            return Optional.empty();
        }

        String tenantId = task.tenantId();
        WorkerRuntimeSnapshot best = null;
        long minUsage = Long.MAX_VALUE;

        for (WorkerRuntimeSnapshot worker : candidates) {
            String key = REDIS_KEY_PREFIX + worker.workerId() + ":" + tenantId;
            String val = redisTemplate.opsForValue().get(key);
            long usage = (val == null) ? 0 : Long.parseLong(val);

            if (usage < minUsage) {
                minUsage = usage;
                best = worker;
            }
        }

        if (best != null) {
            String bestKey = REDIS_KEY_PREFIX + best.workerId() + ":" + tenantId;
            redisTemplate.opsForValue().increment(bestKey);
            // In a real DRR, we'd also have a 'quantum' and reset logic, 
            // but this provides the core 'fairness' by picking the least-used worker for this tenant.
            return Optional.of(best);
        }

        return Optional.of(candidates.get(0));
    }
}

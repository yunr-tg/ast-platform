package com.ast.platform.scheduler.infrastructure.selector;

import com.ast.platform.scheduler.domain.model.ReadyTaskEnvelope;
import com.ast.platform.scheduler.domain.model.WorkerRuntimeSnapshot;
import com.ast.platform.scheduler.domain.service.WorkerSelector;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;
import java.util.List;
import java.util.Optional;

@Component
public class RoundRobinWorkerSelector implements WorkerSelector {

    private final StringRedisTemplate redisTemplate;
    private static final String KEY_PREFIX = "ats:scheduler:rr:index:";

    public RoundRobinWorkerSelector(StringRedisTemplate redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    @Override
    public Optional<WorkerRuntimeSnapshot> select(ReadyTaskEnvelope task, List<WorkerRuntimeSnapshot> candidates) {
        if (candidates == null || candidates.isEmpty()) {
            return Optional.empty();
        }
        
        String key = KEY_PREFIX + task.workerGroup();
        Long next = redisTemplate.opsForValue().increment(key);
        if (next == null) {
            next = 0L;
        }
        
        int index = (int) (next % candidates.size());
        return Optional.of(candidates.get(index));
    }
}

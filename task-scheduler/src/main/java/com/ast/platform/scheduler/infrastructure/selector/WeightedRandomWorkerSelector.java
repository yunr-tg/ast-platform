package com.ast.platform.scheduler.infrastructure.selector;

import com.ast.platform.scheduler.domain.model.ReadyTaskEnvelope;
import com.ast.platform.scheduler.domain.model.WorkerRuntimeSnapshot;
import com.ast.platform.scheduler.domain.service.WorkerSelector;
import org.springframework.stereotype.Component;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ThreadLocalRandom;

@Component
public class WeightedRandomWorkerSelector implements WorkerSelector {
    @Override
    public Optional<WorkerRuntimeSnapshot> select(ReadyTaskEnvelope task, List<WorkerRuntimeSnapshot> candidates) {
        if (candidates == null || candidates.isEmpty()) {
            return Optional.empty();
        }
        
        int totalWeight = candidates.stream().mapToInt(WorkerRuntimeSnapshot::weight).sum();
        if (totalWeight <= 0) {
            // Fallback to simple random if weights are not properly set
            return Optional.of(candidates.get(ThreadLocalRandom.current().nextInt(candidates.size())));
        }
        
        int randomWeight = ThreadLocalRandom.current().nextInt(totalWeight);
        int currentWeight = 0;
        for (WorkerRuntimeSnapshot worker : candidates) {
            currentWeight += worker.weight();
            if (randomWeight < currentWeight) {
                return Optional.of(worker);
            }
        }
        
        return Optional.of(candidates.get(candidates.size() - 1));
    }
}

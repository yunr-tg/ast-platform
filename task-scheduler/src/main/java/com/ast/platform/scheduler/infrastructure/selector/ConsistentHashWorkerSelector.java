package com.ast.platform.scheduler.infrastructure.selector;

import com.ast.platform.scheduler.domain.model.ReadyTaskEnvelope;
import com.ast.platform.scheduler.domain.model.WorkerRuntimeSnapshot;
import com.ast.platform.scheduler.domain.service.WorkerSelector;
import org.springframework.stereotype.Component;
import java.util.List;
import java.util.Optional;

@Component
public class ConsistentHashWorkerSelector implements WorkerSelector {
    @Override
    public Optional<WorkerRuntimeSnapshot> select(ReadyTaskEnvelope task, List<WorkerRuntimeSnapshot> candidates) {
        if (candidates == null || candidates.isEmpty()) {
            return Optional.empty();
        }
        
        // Very simple consistent hash based on taskId
        // For production, a more sophisticated hash ring should be used.
        int hash = Math.abs(task.taskId().hashCode());
        int index = hash % candidates.size();
        
        return Optional.of(candidates.get(index));
    }
}

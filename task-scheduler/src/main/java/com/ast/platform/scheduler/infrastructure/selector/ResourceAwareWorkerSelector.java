package com.ast.platform.scheduler.infrastructure.selector;

import com.ast.platform.scheduler.domain.model.ReadyTaskEnvelope;
import com.ast.platform.scheduler.domain.model.WorkerRuntimeSnapshot;
import com.ast.platform.scheduler.domain.service.WorkerSelector;
import org.springframework.stereotype.Component;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

/**
 * Resource-Aware Scheduling (Bin Packing style)
 * Selects the worker with the lowest resource usage (CPU + Memory).
 */
@Component
public class ResourceAwareWorkerSelector implements WorkerSelector {

    @Override
    public Optional<WorkerRuntimeSnapshot> select(ReadyTaskEnvelope task, List<WorkerRuntimeSnapshot> candidates) {
        if (candidates == null || candidates.isEmpty()) {
            return Optional.empty();
        }

        // Compare by a combined resource score (CPU + Memory)
        // Lower is better (more idle)
        return candidates.stream()
                .min(Comparator.comparingDouble(w -> w.cpuUsage() + w.memoryUsage()));
    }
}

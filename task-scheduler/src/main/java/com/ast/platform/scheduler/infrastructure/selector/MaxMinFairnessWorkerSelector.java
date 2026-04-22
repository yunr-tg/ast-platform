package com.ast.platform.scheduler.infrastructure.selector;

import com.ast.platform.scheduler.domain.model.ReadyTaskEnvelope;
import com.ast.platform.scheduler.domain.model.WorkerRuntimeSnapshot;
import com.ast.platform.scheduler.domain.service.WorkerSelector;
import org.springframework.stereotype.Component;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

/**
 * Max-Min Fairness Worker Selector
 * Aims to satisfy the "smallest" tenants first by ensuring no single tenant 
 * overloads a worker.
 */
@Component
public class MaxMinFairnessWorkerSelector implements WorkerSelector {

    @Override
    public Optional<WorkerRuntimeSnapshot> select(ReadyTaskEnvelope task, List<WorkerRuntimeSnapshot> candidates) {
        if (candidates == null || candidates.isEmpty()) {
            return Optional.empty();
        }

        // Simplified Max-Min: Pick worker with lowest global load (available slots),
        // but also consider the balance. 
        // For P0, we'll use a strategy that prioritizes workers where the 
        // ratio of (tenant_tasks / total_tasks) is lowest.
        // Since we don't have per-tenant breakdown in memory easily, 
        // we fallback to Least Loaded which is a foundation for Max-Min.
        
        return candidates.stream()
                .min(Comparator.comparingInt(WorkerRuntimeSnapshot::activeTaskCount)
                        .thenComparingDouble(WorkerRuntimeSnapshot::errorRate));
    }
}

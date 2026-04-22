package com.ast.platform.scheduler.domain.service;

import com.ast.platform.scheduler.domain.model.ReadyTaskEnvelope;
import com.ast.platform.scheduler.domain.model.WorkerRuntimeSnapshot;
import java.util.List;
import java.util.Optional;

public interface WorkerSelector {
    Optional<WorkerRuntimeSnapshot> select(ReadyTaskEnvelope task, List<WorkerRuntimeSnapshot> candidates);
}

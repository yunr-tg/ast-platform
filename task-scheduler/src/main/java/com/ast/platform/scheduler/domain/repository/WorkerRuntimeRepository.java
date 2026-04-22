package com.ast.platform.scheduler.domain.repository;

import com.ast.platform.scheduler.domain.model.WorkerRuntimeSnapshot;

import java.util.List;
import java.util.Optional;

public interface WorkerRuntimeRepository {

    WorkerRuntimeSnapshot save(WorkerRuntimeSnapshot snapshot);

    Optional<WorkerRuntimeSnapshot> findByWorkerId(String workerId);

    List<WorkerRuntimeSnapshot> findDispatchableWorkers(String workerGroup, String taskType);
}
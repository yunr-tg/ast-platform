package com.ast.platform.scheduler.domain.repository;

import com.ast.platform.scheduler.domain.model.ReadyTaskEnvelope;

import java.time.Instant;
import java.util.Optional;

public interface DispatchQueueRepository {

    void enqueueReady(ReadyTaskEnvelope envelope);

    Optional<ReadyTaskEnvelope> pollNextReadyTask();

    void enqueueRetry(ReadyTaskEnvelope envelope, Instant dueTime);

    int moveDueRetryTasks(Instant now);

    void commitTask(ReadyTaskEnvelope envelope);

    void rollbackTask(ReadyTaskEnvelope envelope);

    int reclaimTimeoutTasks(Instant timeoutThreshold);
}
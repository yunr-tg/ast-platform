package com.ast.platform.scheduler.pump.application;

import com.ast.platform.scheduler.domain.model.ReadyTaskEnvelope;
import com.ast.platform.scheduler.domain.repository.DispatchQueueRepository;
import org.springframework.stereotype.Service;

import java.time.Instant;

@Service
public class PumpIngestApplicationService {

    private final DispatchQueueRepository dispatchQueueRepository;

    public PumpIngestApplicationService(DispatchQueueRepository dispatchQueueRepository) {
        this.dispatchQueueRepository = dispatchQueueRepository;
    }

    public void acceptSubmittedTask(String taskId, String tenantId, String taskType, String workerGroup, String traceId, int priority) {
        dispatchQueueRepository.enqueueReady(new ReadyTaskEnvelope(taskId, tenantId, taskType, workerGroup, traceId, priority, Instant.now()));
    }
}
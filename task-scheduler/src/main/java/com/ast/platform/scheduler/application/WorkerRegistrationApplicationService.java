package com.ast.platform.scheduler.application;

import com.ast.platform.contract.worker.WorkerRegisterRequest;
import com.ast.platform.domain.worker.WorkerStatus;
import com.ast.platform.scheduler.domain.model.WorkerRuntimeSnapshot;
import com.ast.platform.scheduler.domain.repository.WorkerRuntimeRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.Instant;

@Service
public class WorkerRegistrationApplicationService {

    private static final Logger log = LoggerFactory.getLogger(WorkerRegistrationApplicationService.class);

    private final WorkerRuntimeRepository workerRuntimeRepository;

    public WorkerRegistrationApplicationService(WorkerRuntimeRepository workerRuntimeRepository) {
        this.workerRuntimeRepository = workerRuntimeRepository;
    }

    public void registerWorker(WorkerRegisterRequest request) {
        Instant now = Instant.now();
        int weight = request.weight() != null ? request.weight() : 100;
        workerRuntimeRepository.save(new WorkerRuntimeSnapshot(
                request.workerId(), request.workerGroup(), request.host(), request.port(), request.protocol(), request.version(),
                request.supportedTaskTypes(), request.tags(), WorkerStatus.UP, 0, request.maxConcurrency(), request.maxConcurrency(),
                0L, 0.0D, weight, 0.0, 0.0, now, null, now));
        log.info("worker registered, workerId={}, workerGroup={}", request.workerId(), request.workerGroup());
    }
}
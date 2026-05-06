package com.ast.platform.scheduler.application;

import com.ast.platform.contract.worker.WorkerHeartbeatRequest;
import com.ast.platform.domain.worker.WorkerStatus;
import com.ast.platform.scheduler.domain.model.WorkerRuntimeSnapshot;
import com.ast.platform.scheduler.domain.repository.WorkerRuntimeRepository;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;

@Service
public class WorkerHeartbeatApplicationService {

    private static final Logger log = LoggerFactory.getLogger(WorkerHeartbeatApplicationService.class);

    private final WorkerRuntimeRepository workerRuntimeRepository;
    private final Counter heartbeatSuccessCounter;
    private final Counter heartbeatFailureCounter;

    public WorkerHeartbeatApplicationService(WorkerRuntimeRepository workerRuntimeRepository,
                                              MeterRegistry meterRegistry) {
        this.workerRuntimeRepository = workerRuntimeRepository;
        this.heartbeatSuccessCounter = Counter.builder("ast.scheduler.worker.heartbeat")
                .tag("result", "success")
                .register(meterRegistry);
        this.heartbeatFailureCounter = Counter.builder("ast.scheduler.worker.heartbeat")
                .tag("result", "failure")
                .register(meterRegistry);
    }

    @Transactional
    public void processHeartbeat(WorkerHeartbeatRequest request) {
        log.debug("Processing heartbeat from worker: {}, status={}", request.workerId(), request.status());

        try {
            WorkerRuntimeSnapshot existing = workerRuntimeRepository.findByWorkerId(request.workerId()).orElse(null);

            if (existing == null) {
                log.warn("Received heartbeat from unregistered worker: {}, ignoring", request.workerId());
                heartbeatFailureCounter.increment();
                return;
            }

            WorkerStatus effectiveStatus = determineEffectiveStatus(request, existing);
            
            WorkerRuntimeSnapshot updated = new WorkerRuntimeSnapshot(
                    request.workerId(),
                    request.workerGroup(),
                    existing.host(),
                    existing.port(),
                    existing.protocol(),
                    existing.workerVersion(),
                    request.supportedTaskTypes() != null ? request.supportedTaskTypes() : existing.supportedTaskTypes(),
                    existing.tags(),
                    effectiveStatus,
                    request.activeTaskCount(),
                    request.maxConcurrency(),
                    request.availableSlots(),
                    request.avgRt(),
                    request.errorRate(),
                    existing.weight(),
                    request.cpuUsage() > 0 ? request.cpuUsage() : existing.cpuUsage(),
                    request.memoryUsage() > 0 ? request.memoryUsage() : existing.memoryUsage(),
                    existing.lastRegisterAt(),
                    Instant.now(),
                    Instant.now()
            );

            workerRuntimeRepository.save(updated);
            heartbeatSuccessCounter.increment();

            log.debug("Worker heartbeat processed: workerId={}, status={}, activeTasks={}, availableSlots={}",
                    request.workerId(), effectiveStatus, request.activeTaskCount(), request.availableSlots());

            if (effectiveStatus == WorkerStatus.DRAINING) {
                log.info("Worker {} is in DRAINING state with {} active tasks",
                        request.workerId(), request.activeTaskCount());
            }

        } catch (Exception e) {
            log.error("Failed to process heartbeat from worker: {}", request.workerId(), e);
            heartbeatFailureCounter.increment();
            throw e;
        }
    }

    private WorkerStatus determineEffectiveStatus(WorkerHeartbeatRequest request, WorkerRuntimeSnapshot existing) {
        WorkerStatus reportedStatus = request.status();
        WorkerStatus currentStatus = existing.status();

        if (reportedStatus == WorkerStatus.DRAINING) {
            return WorkerStatus.DRAINING;
        }

        if (reportedStatus == WorkerStatus.DOWN) {
            return WorkerStatus.DOWN;
        }

        if (currentStatus == WorkerStatus.DRAINING) {
            if (request.activeTaskCount() == 0) {
                log.info("Worker {} completed draining, transitioning to DOWN", request.workerId());
                return WorkerStatus.DOWN;
            }
            return WorkerStatus.DRAINING;
        }

        if (currentStatus == WorkerStatus.DOWN) {
            log.info("Worker {} transitioning from DOWN to UP", request.workerId());
            return WorkerStatus.UP;
        }

        if (reportedStatus == WorkerStatus.UP || reportedStatus == WorkerStatus.DEGRADED) {
            return reportedStatus;
        }

        return WorkerStatus.UP;
    }

    public List<WorkerRuntimeSnapshot> getAllWorkers() {
        return workerRuntimeRepository.findAll();
    }

    public List<WorkerRuntimeSnapshot> getActiveWorkers() {
        return workerRuntimeRepository.findAll().stream()
                .filter(w -> w.status() == WorkerStatus.UP || w.status() == WorkerStatus.DEGRADED)
                .toList();
    }

    public List<WorkerRuntimeSnapshot> getDrainingWorkers() {
        return workerRuntimeRepository.findAll().stream()
                .filter(w -> w.status() == WorkerStatus.DRAINING)
                .toList();
    }
}

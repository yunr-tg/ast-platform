package com.ast.platform.scheduler.application;

import com.ast.platform.contract.worker.WorkerHeartbeatRequest;
import com.ast.platform.scheduler.domain.model.WorkerRuntimeSnapshot;
import com.ast.platform.scheduler.domain.repository.WorkerRuntimeRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.Instant;

@Service
public class WorkerHeartbeatApplicationService {

    private static final Logger log = LoggerFactory.getLogger(WorkerHeartbeatApplicationService.class);
    private final WorkerRuntimeRepository workerRuntimeRepository;

    public WorkerHeartbeatApplicationService(WorkerRuntimeRepository workerRuntimeRepository) {
        this.workerRuntimeRepository = workerRuntimeRepository;
    }

    public void reportHeartbeat(WorkerHeartbeatRequest request) {
        Instant now = Instant.now();
        WorkerRuntimeSnapshot current = workerRuntimeRepository.findByWorkerId(request.workerId()).orElse(null);
        workerRuntimeRepository.save(new WorkerRuntimeSnapshot(
                request.workerId(), request.workerGroup(), current == null ? null : current.host(), current == null ? null : current.port(),
                current == null ? null : current.protocol(), current == null ? null : current.workerVersion(), request.supportedTaskTypes(),
                current == null ? java.util.List.of() : current.tags(), request.status(), request.activeTaskCount(), request.maxConcurrency(),
                request.availableSlots(), request.avgRt(), request.errorRate(), current == null ? 100 : current.weight(),
                request.cpuUsage() != null ? request.cpuUsage() : 0.0,
                request.memoryUsage() != null ? request.memoryUsage() : 0.0,
                current == null ? null : current.lastRegisterAt(), now, now));
        log.info("worker heartbeat accepted, workerId={}, status={}, availableSlots={}",
                request.workerId(), request.status(), request.availableSlots());
    }
}
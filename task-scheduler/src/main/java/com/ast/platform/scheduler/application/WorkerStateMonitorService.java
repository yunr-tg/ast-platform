package com.ast.platform.scheduler.application;

import com.ast.platform.domain.worker.WorkerStatus;
import com.ast.platform.scheduler.domain.model.WorkerRuntimeSnapshot;
import com.ast.platform.scheduler.domain.repository.WorkerRuntimeRepository;
import com.ast.platform.scheduler.infrastructure.repository.RedisDispatchQueueRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;

@Service
public class WorkerStateMonitorService {

    private static final Logger log = LoggerFactory.getLogger(WorkerStateMonitorService.class);

    private final WorkerRuntimeRepository workerRuntimeRepository;
    private final RedisDispatchQueueRepository dispatchQueueRepository;
    
    private final long heartbeatTimeoutSeconds = 30;
    private final long drainingMaxWaitSeconds = 60;

    public WorkerStateMonitorService(WorkerRuntimeRepository workerRuntimeRepository,
                                      RedisDispatchQueueRepository dispatchQueueRepository) {
        this.workerRuntimeRepository = workerRuntimeRepository;
        this.dispatchQueueRepository = dispatchQueueRepository;
    }

    @Scheduled(fixedDelayString = "${ast.scheduler.worker-monitor-interval-ms:10000}")
    public void monitorWorkerStates() {
        Instant now = Instant.now();
        Instant heartbeatTimeout = now.minus(heartbeatTimeoutSeconds, ChronoUnit.SECONDS);
        Instant drainingTimeout = now.minus(drainingMaxWaitSeconds, ChronoUnit.SECONDS);
        
        List<WorkerRuntimeSnapshot> allWorkers = findAllWorkers();
        
        for (WorkerRuntimeSnapshot worker : allWorkers) {
            handleHeartbeatTimeout(worker, heartbeatTimeout, now);
            handleDrainingTimeout(worker, drainingTimeout, now);
            handleDownState(worker);
        }
    }

    private List<WorkerRuntimeSnapshot> findAllWorkers() {
        return workerRuntimeRepository.findAll();
    }

    private void handleHeartbeatTimeout(WorkerRuntimeSnapshot worker, Instant timeoutThreshold, Instant now) {
        if (worker.status() == WorkerStatus.UP || worker.status() == WorkerStatus.DEGRADED) {
            if (worker.lastHeartbeatAt() == null || worker.lastHeartbeatAt().isBefore(timeoutThreshold)) {
                log.warn("Worker heartbeat timeout detected, marking as DOWN: workerId={}, lastHeartbeat={}", 
                        worker.workerId(), worker.lastHeartbeatAt());
                
                WorkerRuntimeSnapshot downSnapshot = new WorkerRuntimeSnapshot(
                        worker.workerId(), worker.workerGroup(), worker.host(), worker.port(),
                        worker.protocol(), worker.workerVersion(), worker.supportedTaskTypes(),
                        worker.tags(), WorkerStatus.DOWN, 0, worker.maxConcurrency(), 0,
                        worker.avgRt(), worker.errorRate(), worker.weight(),
                        worker.cpuUsage(), worker.memoryUsage(),
                        worker.lastRegisterAt(), worker.lastHeartbeatAt(), now);
                
                workerRuntimeRepository.save(downSnapshot);
                
                reclaimTasksForWorker(worker);
            }
        }
    }

    private void handleDrainingTimeout(WorkerRuntimeSnapshot worker, Instant timeoutThreshold, Instant now) {
        if (worker.status() == WorkerStatus.DRAINING) {
            if (worker.lastHeartbeatAt() == null || worker.lastHeartbeatAt().isBefore(timeoutThreshold)) {
                log.warn("Worker draining timeout, forcing DOWN state: workerId={}, lastHeartbeat={}", 
                        worker.workerId(), worker.lastHeartbeatAt());
                
                WorkerRuntimeSnapshot downSnapshot = new WorkerRuntimeSnapshot(
                        worker.workerId(), worker.workerGroup(), worker.host(), worker.port(),
                        worker.protocol(), worker.workerVersion(), worker.supportedTaskTypes(),
                        worker.tags(), WorkerStatus.DOWN, 0, worker.maxConcurrency(), 0,
                        worker.avgRt(), worker.errorRate(), worker.weight(),
                        worker.cpuUsage(), worker.memoryUsage(),
                        worker.lastRegisterAt(), worker.lastHeartbeatAt(), now);
                
                workerRuntimeRepository.save(downSnapshot);
                
                reclaimTasksForWorker(worker);
            } else if (worker.activeTaskCount() == 0) {
                log.info("Worker draining completed with no active tasks, marking as DOWN: workerId={}", 
                        worker.workerId());
                
                WorkerRuntimeSnapshot downSnapshot = new WorkerRuntimeSnapshot(
                        worker.workerId(), worker.workerGroup(), worker.host(), worker.port(),
                        worker.protocol(), worker.workerVersion(), worker.supportedTaskTypes(),
                        worker.tags(), WorkerStatus.DOWN, 0, worker.maxConcurrency(), 0,
                        worker.avgRt(), worker.errorRate(), worker.weight(),
                        worker.cpuUsage(), worker.memoryUsage(),
                        worker.lastRegisterAt(), now, now);
                
                workerRuntimeRepository.save(downSnapshot);
            }
        }
    }

    private void handleDownState(WorkerRuntimeSnapshot worker) {
        if (worker.status() == WorkerStatus.DOWN && worker.activeTaskCount() > 0) {
            log.warn("Worker in DOWN state still has active tasks, reclaiming: workerId={}, activeTasks={}", 
                    worker.workerId(), worker.activeTaskCount());
            reclaimTasksForWorker(worker);
        }
    }

    private void reclaimTasksForWorker(WorkerRuntimeSnapshot worker) {
        log.info("Reclaiming tasks for unavailable worker: workerId={}, workerGroup={}", 
                worker.workerId(), worker.workerGroup());
        
        dispatchQueueRepository.reclaimTimeoutTasks(Instant.now());
    }
}
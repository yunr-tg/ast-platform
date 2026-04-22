package com.ast.platform.scheduler.dispatcher.application;

import com.ast.platform.domain.task.SchedulingStrategy;
import com.ast.platform.domain.task.TaskStatus;
import com.ast.platform.scheduler.config.SchedulerProperties;
import com.ast.platform.scheduler.dispatcher.domain.SchedulingStrategyResolver;
import com.ast.platform.scheduler.domain.gateway.WorkerDispatchGateway;
import com.ast.platform.scheduler.domain.model.DispatchRecord;
import com.ast.platform.scheduler.domain.model.ReadyTaskEnvelope;
import com.ast.platform.scheduler.domain.model.SchedulerTaskSnapshot;
import com.ast.platform.scheduler.domain.model.WorkerRuntimeSnapshot;
import com.ast.platform.scheduler.domain.repository.DispatchQueueRepository;
import com.ast.platform.scheduler.domain.repository.DispatchRecordRepository;
import com.ast.platform.scheduler.domain.repository.SchedulerTaskRepository;
import com.ast.platform.scheduler.domain.repository.WorkerRuntimeRepository;
import com.ast.platform.scheduler.domain.service.WorkerSelector;
import com.ast.platform.scheduler.infrastructure.selector.*;
import io.micrometer.tracing.Span;
import io.micrometer.tracing.Tracer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Service
public class DispatcherApplicationService {

    private static final Logger log = LoggerFactory.getLogger(DispatcherApplicationService.class);

    private final DispatchQueueRepository dispatchQueueRepository;
    private final SchedulerTaskRepository schedulerTaskRepository;
    private final WorkerRuntimeRepository workerRuntimeRepository;
    private final DispatchRecordRepository dispatchRecordRepository;
    private final WorkerDispatchGateway workerDispatchGateway;
    private final SchedulerProperties schedulerProperties;
    private final SchedulingStrategyResolver strategyResolver;
    private final Tracer tracer;
    
    private final LeastLoadedWorkerSelector leastLoadedSelector;
    private final RandomWorkerSelector randomSelector;
    private final RoundRobinWorkerSelector roundRobinSelector;
    private final ConsistentHashWorkerSelector consistentHashSelector;
    private final WeightedRandomWorkerSelector weightedRandomSelector;
    private final WeightedRoundRobinWorkerSelector weightedRoundRobinSelector;
    private final ResourceAwareWorkerSelector resourceAwareSelector;
    private final DeficitRoundRobinWorkerSelector drrSelector;
    private final MaxMinFairnessWorkerSelector maxMinFairnessSelector;

    public DispatcherApplicationService(DispatchQueueRepository dispatchQueueRepository,
                                        SchedulerTaskRepository schedulerTaskRepository,
                                        WorkerRuntimeRepository workerRuntimeRepository,
                                        DispatchRecordRepository dispatchRecordRepository,
                                        WorkerDispatchGateway workerDispatchGateway,
                                        SchedulerProperties schedulerProperties,
                                        SchedulingStrategyResolver strategyResolver,
                                        Tracer tracer,
                                        LeastLoadedWorkerSelector leastLoadedSelector,
                                        RandomWorkerSelector randomSelector,
                                        RoundRobinWorkerSelector roundRobinSelector,
                                        ConsistentHashWorkerSelector consistentHashSelector,
                                        WeightedRandomWorkerSelector weightedRandomSelector,
                                        WeightedRoundRobinWorkerSelector weightedRoundRobinSelector,
                                        ResourceAwareWorkerSelector resourceAwareSelector,
                                        DeficitRoundRobinWorkerSelector drrSelector,
                                        MaxMinFairnessWorkerSelector maxMinFairnessSelector) {
        this.dispatchQueueRepository = dispatchQueueRepository;
        this.schedulerTaskRepository = schedulerTaskRepository;
        this.workerRuntimeRepository = workerRuntimeRepository;
        this.dispatchRecordRepository = dispatchRecordRepository;
        this.workerDispatchGateway = workerDispatchGateway;
        this.schedulerProperties = schedulerProperties;
        this.strategyResolver = strategyResolver;
        this.tracer = tracer;
        this.leastLoadedSelector = leastLoadedSelector;
        this.randomSelector = randomSelector;
        this.roundRobinSelector = roundRobinSelector;
        this.consistentHashSelector = consistentHashSelector;
        this.weightedRandomSelector = weightedRandomSelector;
        this.weightedRoundRobinSelector = weightedRoundRobinSelector;
        this.resourceAwareSelector = resourceAwareSelector;
        this.drrSelector = drrSelector;
        this.maxMinFairnessSelector = maxMinFairnessSelector;
    }

    public boolean dispatchNext() {
        Optional<ReadyTaskEnvelope> nextTask = dispatchQueueRepository.pollNextReadyTask();
        if (nextTask.isEmpty()) {
            return false;
        }

        ReadyTaskEnvelope envelope = nextTask.get();
        // Start a new span for dispatching. 
        // We link it via MDC for now, as full context propagation across Redis is not yet standard in P0.
        Span span = tracer.nextSpan().name("dispatch").start();
        try (Tracer.SpanInScope ws = tracer.withSpan(span)) {
            // For logging consistency, we ensure MDC has the business traceId from the envelope
            MDC.put("traceId", envelope.traceId());
            return doDispatch(envelope);
        } finally {
            span.end();
            MDC.remove("traceId");
        }
    }

    private boolean doDispatch(ReadyTaskEnvelope envelope) {
        SchedulerTaskSnapshot task = schedulerTaskRepository.findByTaskId(envelope.taskId()).orElse(null);
        if (task == null || task.status() != TaskStatus.QUEUED) {
            dispatchQueueRepository.commitTask(envelope); // Should not happen often
            return false;
        }

        List<WorkerRuntimeSnapshot> workers = workerRuntimeRepository.findDispatchableWorkers(envelope.workerGroup(), envelope.taskType());
        if (workers.isEmpty()) {
            dispatchQueueRepository.rollbackTask(envelope);
            return false;
        }
        
        SchedulingStrategy strategy = strategyResolver.resolve(envelope);
        WorkerSelector selector = getSelector(strategy);
        WorkerRuntimeSnapshot worker = selector.select(envelope, workers).orElse(null);
        if (worker == null) {
            dispatchQueueRepository.rollbackTask(envelope);
            return false;
        }
        
        String dispatchToken = UUID.randomUUID().toString();
        if (!workerDispatchGateway.dispatch(envelope, task, worker, dispatchToken)) {
            dispatchQueueRepository.rollbackTask(envelope);
            return false;
        }
        dispatchQueueRepository.commitTask(envelope);
        if (!schedulerTaskRepository.advanceStatus(envelope.taskId(), TaskStatus.QUEUED, TaskStatus.DISPATCHED)) {
            return false;
        }

        Instant now = Instant.now();
        dispatchRecordRepository.save(new DispatchRecord(
                envelope.taskId(), envelope.tenantId(), envelope.taskType(), envelope.workerGroup(), worker.workerId(), dispatchToken,
                "RUNNING", envelope.traceId(), 0, null, null, null, now, now, now));
        log.info("task dispatched using {}, taskId={}, workerId={}, dispatchToken={}", 
                strategy, envelope.taskId(), worker.workerId(), dispatchToken);
        return true;
    }
    
    private WorkerSelector getSelector(SchedulingStrategy strategy) {
        return switch (strategy) {
            case LEAST_LOADED -> leastLoadedSelector;
            case RANDOM -> randomSelector;
            case ROUND_ROBIN -> roundRobinSelector;
            case CONSISTENT_HASH -> consistentHashSelector;
            case WEIGHTED_RANDOM -> weightedRandomSelector;
            case WEIGHTED_ROUND_ROBIN -> weightedRoundRobinSelector;
            case RESOURCE_AWARE -> resourceAwareSelector;
            case FAIR_DRR -> drrSelector;
            case MAX_MIN_FAIRNESS -> maxMinFairnessSelector;
            default -> leastLoadedSelector;
        };
    }
}

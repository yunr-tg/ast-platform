package com.ast.platform.scheduler.infrastructure.repository;

import com.ast.platform.scheduler.domain.model.ReadyTaskEnvelope;
import com.ast.platform.scheduler.domain.repository.DispatchQueueRepository;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.Comparator;
import java.util.Map;
import java.util.Optional;
import java.util.PriorityQueue;
import java.util.Queue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;

@Repository
@ConditionalOnProperty(prefix = "ast.scheduler", name = "local-queue-enabled", havingValue = "true", matchIfMissing = true)
public class LocalDispatchQueueRepository implements DispatchQueueRepository {

    private final Map<String, Queue<ReadyTaskEnvelope>> readyQueues = new ConcurrentHashMap<>();
    private final Queue<String> activeKeys = new ConcurrentLinkedQueue<>();
    private final PriorityQueue<RetryEntry> retryQueue = new PriorityQueue<>(Comparator.comparing(RetryEntry::dueTime));

    @Override
    public synchronized void enqueueReady(ReadyTaskEnvelope envelope) {
        readyQueues.computeIfAbsent(envelope.activeKey(), ignored -> new ConcurrentLinkedQueue<>()).offer(envelope);
        activeKeys.offer(envelope.activeKey());
    }

    @Override
    public synchronized Optional<ReadyTaskEnvelope> pollNextReadyTask() {
        int rounds = activeKeys.size();
        while (rounds-- > 0) {
            String key = activeKeys.poll();
            if (key == null) {
                break;
            }
            Queue<ReadyTaskEnvelope> queue = readyQueues.get(key);
            if (queue == null) {
                continue;
            }
            ReadyTaskEnvelope envelope = queue.poll();
            if (queue.peek() != null) {
                activeKeys.offer(key);
            }
            if (envelope != null) {
                return Optional.of(envelope);
            }
        }
        return Optional.empty();
    }

    @Override
    public synchronized void enqueueRetry(ReadyTaskEnvelope envelope, Instant dueTime) {
        retryQueue.offer(new RetryEntry(envelope, dueTime));
    }

    @Override
    public synchronized int moveDueRetryTasks(Instant now) {
        int moved = 0;
        while (!retryQueue.isEmpty() && !retryQueue.peek().dueTime().isAfter(now)) {
            enqueueReady(retryQueue.poll().envelope());
            moved++;
        }
        return moved;
    }

    @Override
    public void commitTask(ReadyTaskEnvelope envelope) {
        // Local queue doesn't have a processing state in P0
    }

    @Override
    public void rollbackTask(ReadyTaskEnvelope envelope) {
        // Local queue doesn't have a processing state in P0
        enqueueReady(envelope);
    }

    @Override
    public int reclaimTimeoutTasks(Instant timeoutThreshold) {
        return 0;
    }

    private record RetryEntry(ReadyTaskEnvelope envelope, Instant dueTime) {
    }
}
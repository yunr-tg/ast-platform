package com.ast.platform.gateway.infrastructure.repository;

import com.ast.platform.domain.task.TaskPublishOutboxStatus;
import com.ast.platform.gateway.domain.model.TaskPublishOutbox;
import com.ast.platform.gateway.domain.repository.TaskPublishOutboxRepository;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Repository;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

@Repository
@ConditionalOnProperty(prefix = "ast.gateway", name = "storage-type", havingValue = "in-memory")
public class InMemoryTaskPublishOutboxRepository implements TaskPublishOutboxRepository {

    private final Map<String, TaskPublishOutbox> outboxStore = new ConcurrentHashMap<>();
    private final Map<String, String> taskIndex = new ConcurrentHashMap<>();

    @Override
    public TaskPublishOutbox save(TaskPublishOutbox outbox) {
        outboxStore.put(outbox.outboxId(), outbox);
        taskIndex.put(outbox.taskId(), outbox.outboxId());
        return outbox;
    }

    @Override
    public Optional<TaskPublishOutbox> findByTaskId(String taskId) {
        return Optional.ofNullable(taskIndex.get(taskId))
                .map(outboxStore::get);
    }

    @Override
    public List<TaskPublishOutbox> findByStatus(TaskPublishOutboxStatus status, int limit) {
        return outboxStore.values().stream()
                .filter(outbox -> outbox.status() == status)
                .filter(outbox -> outbox.nextRetryTime() == null || !outbox.nextRetryTime().isAfter(java.time.Instant.now()))
                .sorted(Comparator.comparing(TaskPublishOutbox::createdAt))
                .limit(limit)
                .toList();
    }
}

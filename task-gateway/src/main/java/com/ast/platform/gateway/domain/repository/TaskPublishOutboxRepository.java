package com.ast.platform.gateway.domain.repository;

import com.ast.platform.domain.task.TaskPublishOutboxStatus;
import com.ast.platform.gateway.domain.model.TaskPublishOutbox;

import java.util.List;
import java.util.Optional;

public interface TaskPublishOutboxRepository {

    TaskPublishOutbox save(TaskPublishOutbox outbox);

    Optional<TaskPublishOutbox> findByTaskId(String taskId);

    List<TaskPublishOutbox> findByStatus(TaskPublishOutboxStatus status, int limit);
}

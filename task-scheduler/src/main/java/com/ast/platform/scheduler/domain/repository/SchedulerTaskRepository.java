package com.ast.platform.scheduler.domain.repository;

import com.ast.platform.domain.task.TaskStatus;
import com.ast.platform.scheduler.domain.model.SchedulerTaskSnapshot;

import java.util.Optional;

public interface SchedulerTaskRepository {

    Optional<SchedulerTaskSnapshot> findByTaskId(String taskId);

    java.util.List<SchedulerTaskSnapshot> findTasksByStatusInAndUpdatedBefore(java.util.Set<com.ast.platform.domain.task.TaskStatus> statuses, java.time.Instant threshold, int limit);

    boolean advanceStatus(String taskId, TaskStatus expectedStatus, TaskStatus targetStatus);

    void updateProgress(String taskId, int percentage);
}
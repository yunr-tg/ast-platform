package com.ast.platform.domain.task;

import java.util.Optional;

/**
 * Repository for task progress tracking.
 */
public interface TaskProgressRepository {

    /**
     * Save progress snapshot (L1) and append to timeline (L2).
     */
    void saveProgress(TaskProgress progress, long sequence);

    /**
     * Get current progress snapshot.
     */
    Optional<TaskProgress> getLatestProgress(String taskId);
}

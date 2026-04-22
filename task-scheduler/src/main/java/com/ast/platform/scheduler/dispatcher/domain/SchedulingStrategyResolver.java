package com.ast.platform.scheduler.dispatcher.domain;

import com.ast.platform.domain.task.SchedulingStrategy;
import com.ast.platform.scheduler.config.SchedulerProperties;
import com.ast.platform.scheduler.domain.model.ReadyTaskEnvelope;
import org.springframework.stereotype.Component;

/**
 * Resolves the appropriate scheduling strategy for a given task based on its context.
 */
@Component
public class SchedulingStrategyResolver {

    private final SchedulerProperties schedulerProperties;

    public SchedulingStrategyResolver(SchedulerProperties schedulerProperties) {
        this.schedulerProperties = schedulerProperties;
    }

    /**
     * Resolves strategy: TaskType Specific -> Global Default
     */
    public SchedulingStrategy resolve(ReadyTaskEnvelope task) {
        if (schedulerProperties.getTypeStrategies().containsKey(task.taskType())) {
            return schedulerProperties.getTypeStrategies().get(task.taskType());
        }
        return schedulerProperties.getStrategy();
    }
}

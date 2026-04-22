package com.ast.platform.domain.task;

import com.ast.platform.common.exception.BusinessException;
import com.ast.platform.common.exception.ErrorCode;

public final class TaskStateMachine {

    private TaskStateMachine() {
    }

    public static void requireTransition(TaskStatus current, TaskStatus target) {
        if (!current.canTransitTo(target)) {
            throw new BusinessException(
                    ErrorCode.ILLEGAL_STATE_TRANSITION,
                    "Illegal task status transition: " + current + " -> " + target
            );
        }
    }
}

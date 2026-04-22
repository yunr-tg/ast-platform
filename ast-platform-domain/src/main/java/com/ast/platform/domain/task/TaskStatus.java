package com.ast.platform.domain.task;

import java.util.EnumSet;
import java.util.Set;

public enum TaskStatus {
    INIT,
    QUEUED,
    DISPATCHED,
    RUNNING,
    SUCCESS,
    FAILED,
    RETRY_WAIT,
    DEAD_LETTER,
    CANCELLED;

    private static final Set<TaskStatus> TERMINAL_STATUSES = EnumSet.of(SUCCESS, FAILED, DEAD_LETTER, CANCELLED);

    public boolean isTerminal() {
        return TERMINAL_STATUSES.contains(this);
    }

    public boolean canTransitTo(TaskStatus target) {
        if (this == target) {
            return true;
        }
        if (isTerminal()) {
            return false;
        }
        return switch (this) {
            case INIT -> EnumSet.of(QUEUED, CANCELLED).contains(target);
            case QUEUED -> EnumSet.of(DISPATCHED, CANCELLED).contains(target);
            case DISPATCHED -> EnumSet.of(RUNNING, SUCCESS, FAILED, RETRY_WAIT, DEAD_LETTER, CANCELLED).contains(target);
            case RUNNING -> EnumSet.of(SUCCESS, RETRY_WAIT, FAILED, DEAD_LETTER).contains(target);
            case RETRY_WAIT -> target == QUEUED;
            default -> false;
        };
    }
}

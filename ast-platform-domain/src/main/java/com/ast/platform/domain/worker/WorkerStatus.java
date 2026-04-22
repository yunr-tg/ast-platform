package com.ast.platform.domain.worker;

public enum WorkerStatus {
    UP,
    DEGRADED,
    DRAINING,
    DOWN,
    BLOCKED;

    public boolean canReceiveTask() {
        return this == UP || this == DEGRADED;
    }
}

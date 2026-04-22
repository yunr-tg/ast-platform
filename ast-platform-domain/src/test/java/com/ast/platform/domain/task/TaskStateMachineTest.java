package com.ast.platform.domain.task;

import com.ast.platform.common.exception.BusinessException;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

class TaskStateMachineTest {

    @Test
    void shouldAllowConfiguredTransitions() {
        assertDoesNotThrow(() -> TaskStateMachine.requireTransition(TaskStatus.INIT, TaskStatus.QUEUED));
        assertDoesNotThrow(() -> TaskStateMachine.requireTransition(TaskStatus.RUNNING, TaskStatus.RETRY_WAIT));
        assertDoesNotThrow(() -> TaskStateMachine.requireTransition(TaskStatus.RETRY_WAIT, TaskStatus.QUEUED));
    }

    @Test
    void shouldRejectTerminalOverride() {
        assertThrows(BusinessException.class,
                () -> TaskStateMachine.requireTransition(TaskStatus.SUCCESS, TaskStatus.FAILED));
    }
}

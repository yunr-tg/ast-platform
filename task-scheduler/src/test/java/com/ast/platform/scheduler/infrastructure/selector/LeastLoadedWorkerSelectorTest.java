package com.ast.platform.scheduler.infrastructure.selector;

import com.ast.platform.domain.worker.WorkerStatus;
import com.ast.platform.scheduler.domain.model.ReadyTaskEnvelope;
import com.ast.platform.scheduler.domain.model.WorkerRuntimeSnapshot;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

class LeastLoadedWorkerSelectorTest {

    private final LeastLoadedWorkerSelector selector = new LeastLoadedWorkerSelector();

    @Test
    void shouldSelectFirstWorkerFromSortedCandidates() {
        List<WorkerRuntimeSnapshot> workers = List.of(
                createWorker("worker-1", 1, 10),
                createWorker("worker-2", 3, 10),
                createWorker("worker-3", 5, 10)
        );

        ReadyTaskEnvelope task = createTask("task-1");
        Optional<WorkerRuntimeSnapshot> selected = selector.select(task, workers);

        assertThat(selected).isPresent();
        assertThat(selected.get().workerId()).isEqualTo("worker-1");
    }

    @Test
    void shouldSelectWorkerWithMostAvailableSlotsWhenSorted() {
        List<WorkerRuntimeSnapshot> workers = List.of(
                createWorker("worker-3", 2, 10),
                createWorker("worker-1", 5, 10),
                createWorker("worker-2", 8, 10)
        );

        ReadyTaskEnvelope task = createTask("task-1");
        Optional<WorkerRuntimeSnapshot> selected = selector.select(task, workers);

        assertThat(selected).isPresent();
        assertThat(selected.get().workerId()).isEqualTo("worker-3");
    }

    @Test
    void shouldReturnEmptyWhenNoWorkersAvailable() {
        List<WorkerRuntimeSnapshot> workers = List.of();

        ReadyTaskEnvelope task = createTask("task-1");
        Optional<WorkerRuntimeSnapshot> selected = selector.select(task, workers);

        assertThat(selected).isEmpty();
    }

    @Test
    void shouldReturnFirstWorkerEvenWhenAllFull() {
        List<WorkerRuntimeSnapshot> workers = List.of(
                createWorkerWithSlots("worker-1", 10, 10, 0),
                createWorkerWithSlots("worker-2", 10, 10, 0)
        );

        ReadyTaskEnvelope task = createTask("task-1");
        Optional<WorkerRuntimeSnapshot> selected = selector.select(task, workers);

        assertThat(selected).isPresent();
        assertThat(selected.get().workerId()).isEqualTo("worker-1");
    }

    private ReadyTaskEnvelope createTask(String taskId) {
        return new ReadyTaskEnvelope(taskId, "tenant-1", "test-task", "test-group", "trace-1", 5, Instant.now());
    }

    private WorkerRuntimeSnapshot createWorker(String workerId, int activeTaskCount, int maxConcurrency) {
        int availableSlots = maxConcurrency - activeTaskCount;
        return createWorkerWithSlots(workerId, activeTaskCount, maxConcurrency, availableSlots);
    }

    private WorkerRuntimeSnapshot createWorkerWithSlots(String workerId, int activeTaskCount, int maxConcurrency, int availableSlots) {
        return new WorkerRuntimeSnapshot(
                workerId, "test-group", "127.0.0.1", 19091, "http", "1.0.0",
                List.of("test-task"), List.of("default"), WorkerStatus.UP, activeTaskCount, maxConcurrency,
                availableSlots, 100L, 0.0, 100, 0.0, 0.0,
                Instant.now(), Instant.now(), Instant.now()
        );
    }
}

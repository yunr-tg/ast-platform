package com.ast.platform.scheduler.infrastructure.selector;

import com.ast.platform.domain.worker.WorkerStatus;
import com.ast.platform.scheduler.domain.model.ReadyTaskEnvelope;
import com.ast.platform.scheduler.domain.model.WorkerRuntimeSnapshot;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

class RoundRobinWorkerSelectorTest {

    private StringRedisTemplate redisTemplate;
    private ValueOperations<String, String> valueOps;
    private RoundRobinWorkerSelector selector;

    @BeforeEach
    void setUp() {
        redisTemplate = mock(StringRedisTemplate.class);
        valueOps = mock(ValueOperations.class);
        when(redisTemplate.opsForValue()).thenReturn(valueOps);
        selector = new RoundRobinWorkerSelector(redisTemplate);
    }

    @Test
    void shouldSelectWorkersInRoundRobinOrder() {
        List<WorkerRuntimeSnapshot> workers = List.of(
                createWorker("worker-1"),
                createWorker("worker-2"),
                createWorker("worker-3")
        );

        ReadyTaskEnvelope task = createTask("task-1");
        
        when(valueOps.increment(anyString())).thenReturn(0L, 1L, 2L, 3L);

        Optional<WorkerRuntimeSnapshot> first = selector.select(task, workers);
        Optional<WorkerRuntimeSnapshot> second = selector.select(task, workers);
        Optional<WorkerRuntimeSnapshot> third = selector.select(task, workers);
        Optional<WorkerRuntimeSnapshot> fourth = selector.select(task, workers);

        assertThat(first).isPresent();
        assertThat(first.get().workerId()).isEqualTo("worker-1");
        assertThat(second).isPresent();
        assertThat(second.get().workerId()).isEqualTo("worker-2");
        assertThat(third).isPresent();
        assertThat(third.get().workerId()).isEqualTo("worker-3");
        assertThat(fourth).isPresent();
        assertThat(fourth.get().workerId()).isEqualTo("worker-1");
    }

    @Test
    void shouldReturnEmptyWhenNoWorkersAvailable() {
        List<WorkerRuntimeSnapshot> workers = List.of();

        ReadyTaskEnvelope task = createTask("task-1");
        Optional<WorkerRuntimeSnapshot> selected = selector.select(task, workers);

        assertThat(selected).isEmpty();
    }

    @Test
    void shouldSelectFromAvailableWorkers() {
        List<WorkerRuntimeSnapshot> workers = List.of(
                createWorkerWithSlots("worker-1", 10, 10, 0),
                createWorker("worker-2"),
                createWorker("worker-3")
        );

        ReadyTaskEnvelope task = createTask("task-1");
        when(valueOps.increment(anyString())).thenReturn(0L);

        Optional<WorkerRuntimeSnapshot> selected = selector.select(task, workers);

        assertThat(selected).isPresent();
        assertThat(selected.get().workerId()).isEqualTo("worker-1");
    }

    private ReadyTaskEnvelope createTask(String taskId) {
        return new ReadyTaskEnvelope(taskId, "tenant-1", "test-task", "test-group", "trace-1", 5, Instant.now());
    }

    private WorkerRuntimeSnapshot createWorker(String workerId) {
        return createWorkerWithSlots(workerId, 0, 10, 10);
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

package com.ast.platform.scheduler;

import com.ast.platform.contract.worker.WorkerHeartbeatRequest;
import com.ast.platform.contract.worker.WorkerRegisterRequest;
import com.ast.platform.domain.worker.WorkerStatus;
import com.ast.platform.scheduler.application.WorkerHeartbeatApplicationService;
import com.ast.platform.scheduler.application.WorkerRegistrationApplicationService;
import com.ast.platform.scheduler.domain.model.WorkerRuntimeSnapshot;
import com.ast.platform.scheduler.domain.repository.WorkerRuntimeRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:ast_scheduler_worker;MODE=MySQL;DB_CLOSE_DELAY=-1;DATABASE_TO_UPPER=false"
})
class WorkerControlPlaneIntegrationTest {

    @Autowired
    private WorkerRegistrationApplicationService registrationApplicationService;
    @Autowired
    private WorkerHeartbeatApplicationService heartbeatApplicationService;
    @Autowired
    private WorkerRuntimeRepository workerRuntimeRepository;

    @Test
    void shouldPersistWorkerRegistrationAndHeartbeat() {
        registrationApplicationService.registerWorker(new WorkerRegisterRequest(
                "worker-1", "render-group", "127.0.0.1", 19090, "http", "1.0.0", List.of("render-task"), List.of("default"), 8, 100));
        heartbeatApplicationService.reportHeartbeat(new WorkerHeartbeatRequest(
                "worker-1", "render-group", List.of("render-task"), WorkerStatus.UP, 1, 8, 7, 50L, 0.02D, 0.5, 0.6));

        WorkerRuntimeSnapshot snapshot = workerRuntimeRepository.findByWorkerId("worker-1").orElseThrow();
        assertThat(snapshot.workerGroup()).isEqualTo("render-group");
        assertThat(snapshot.availableSlots()).isEqualTo(7);
        assertThat(snapshot.supportedTaskTypes()).contains("render-task");
        assertThat(snapshot.lastRegisterAt()).isNotNull();
        assertThat(snapshot.lastHeartbeatAt()).isNotNull();
    }
}
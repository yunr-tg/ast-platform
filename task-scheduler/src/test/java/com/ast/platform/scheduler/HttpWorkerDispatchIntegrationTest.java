package com.ast.platform.scheduler;

import com.ast.platform.contract.worker.WorkerRegisterRequest;
import com.ast.platform.domain.task.TaskStatus;
import com.ast.platform.scheduler.application.WorkerRegistrationApplicationService;
import com.ast.platform.scheduler.dispatcher.application.DispatcherApplicationService;
import com.ast.platform.scheduler.domain.repository.DispatchQueueRepository;
import com.ast.platform.scheduler.pump.application.PumpIngestApplicationService;
import com.github.tomakehurst.wiremock.junit5.WireMockTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;

import static com.github.tomakehurst.wiremock.client.WireMock.*;
import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:ast_scheduler_http_dispatch;MODE=MySQL;DB_CLOSE_DELAY=-1;DATABASE_TO_UPPER=false",
        "ast.scheduler.worker-dispatch.type=http",
        "ast.scheduler.local-queue-enabled=true",
        "ast.worker-sdk.worker-id=worker-http",
        "ast.worker-sdk.scheduler-base-url=http://localhost:8081"
})
@WireMockTest(httpPort = 19091)
class HttpWorkerDispatchIntegrationTest {

    @Autowired private JdbcTemplate jdbcTemplate;
    @Autowired private PumpIngestApplicationService pumpIngestApplicationService;
    @Autowired private WorkerRegistrationApplicationService workerRegistrationApplicationService;
    @Autowired private DispatcherApplicationService dispatcherApplicationService;
    @Autowired private DispatchQueueRepository dispatchQueueRepository;

    @BeforeEach
    void setUp() {
        jdbcTemplate.execute("delete from gateway_task");
        jdbcTemplate.execute("delete from scheduler_worker");
        // Clear local queue
        while (dispatchQueueRepository.pollNextReadyTask().isPresent());
    }

    @Test
    void shouldDispatchTaskViaHttp() {
        Instant now = Instant.now();
        jdbcTemplate.update("""
                insert into gateway_task(task_id, tenant_id, task_type, biz_key, request_id, worker_group, tag, payload, callback_url, trace_id, status, version, created_at, updated_at)
                values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """,
                "task-http-1", "tenant-a", "render-task", "biz-1", "req-1", "render-group", "default", "{\"a\":1}",
                "http://callback.test/result", "trace-1", TaskStatus.QUEUED.name(), 0, Timestamp.from(now), Timestamp.from(now));

        workerRegistrationApplicationService.registerWorker(new WorkerRegisterRequest(
                "worker-http", "render-group", "127.0.0.1", 19091, "http", "1.0.0", List.of("render-task"), List.of("default"), 4, 100));
        
        pumpIngestApplicationService.acceptSubmittedTask("task-http-1", "tenant-a", "render-task", "render-group", "trace-1", 5);

        // Mock the HTTP response from Worker SDK
        stubFor(post(urlEqualTo("/worker/dispatch"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("{\"success\":true, \"message\":\"Accepted\"}")));

        boolean result = dispatcherApplicationService.dispatchNext();
        
        assertThat(result).isTrue();
        
        verify(postRequestedFor(urlEqualTo("/worker/dispatch"))
                .withRequestBody(matchingJsonPath("$.taskId", equalTo("task-http-1")))
                .withRequestBody(matchingJsonPath("$.taskType", equalTo("render-task"))));
    }
}

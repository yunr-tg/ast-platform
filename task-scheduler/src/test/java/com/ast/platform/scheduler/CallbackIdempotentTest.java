package com.ast.platform.scheduler;

import com.ast.platform.contract.worker.WorkerCallbackRequest;
import com.ast.platform.contract.worker.WorkerRegisterRequest;
import com.ast.platform.domain.task.TaskStatus;
import com.ast.platform.scheduler.application.WorkerRegistrationApplicationService;
import com.ast.platform.scheduler.callback.application.WorkerCallbackApplicationService;
import com.ast.platform.scheduler.dispatcher.application.DispatcherApplicationService;
import com.ast.platform.scheduler.domain.model.DispatchRecord;
import com.ast.platform.scheduler.domain.model.NotifyOutboxRecord;
import com.ast.platform.scheduler.domain.repository.DispatchRecordRepository;
import com.ast.platform.scheduler.domain.repository.NotifyOutboxRepository;
import com.ast.platform.scheduler.domain.repository.SchedulerTaskRepository;
import com.ast.platform.scheduler.pump.application.PumpIngestApplicationService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNoException;

@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:ast_callback_idempotent;MODE=MySQL;DB_CLOSE_DELAY=-1;DATABASE_TO_UPPER=false",
        "ast.scheduler.local-queue-enabled=true"
})
class CallbackIdempotentTest {

    @Autowired private JdbcTemplate jdbcTemplate;
    @Autowired private PumpIngestApplicationService pumpIngestApplicationService;
    @Autowired private WorkerRegistrationApplicationService workerRegistrationApplicationService;
    @Autowired private DispatcherApplicationService dispatcherApplicationService;
    @Autowired private WorkerCallbackApplicationService workerCallbackApplicationService;
    @Autowired private DispatchRecordRepository dispatchRecordRepository;
    @Autowired private SchedulerTaskRepository schedulerTaskRepository;
    @Autowired private NotifyOutboxRepository notifyOutboxRepository;

    @Test
    void shouldHandleDuplicateSuccessCallback() {
        String taskId = "task-callback-dup-1";
        String requestId = "req-callback-dup-1";
        setupTaskAndDispatch(taskId, requestId);

        DispatchRecord dispatchRecord = dispatchRecordRepository.findByTaskId(taskId).orElseThrow();
        String dispatchToken = dispatchRecord.dispatchToken();

        workerCallbackApplicationService.acceptCallback(new WorkerCallbackRequest(
                taskId, "worker-callback", dispatchToken, "trace-callback", 
                TaskStatus.SUCCESS, false, "{\"result\":\"ok\"}", null));

        assertThat(schedulerTaskRepository.findByTaskId(taskId).orElseThrow().status())
                .isEqualTo(TaskStatus.SUCCESS);

        assertThatNoException().isThrownBy(() ->
            workerCallbackApplicationService.acceptCallback(new WorkerCallbackRequest(
                    taskId, "worker-callback", dispatchToken, "trace-callback", 
                    TaskStatus.SUCCESS, false, "{\"result\":\"ok\"}", null))
        );

        List<NotifyOutboxRecord> outboxes = notifyOutboxRepository.findDueRecords("NEW", Instant.now(), 10);
        long taskNotifyCount = outboxes.stream().filter(o -> o.taskId().equals(taskId)).count();
        assertThat(taskNotifyCount).isEqualTo(1);
    }

    @Test
    void shouldRejectCallbackWithInvalidToken() {
        String taskId = "task-invalid-token";
        String requestId = "req-invalid-token";
        setupTaskAndDispatch(taskId, requestId);

        DispatchRecord dispatchRecord = dispatchRecordRepository.findByTaskId(taskId).orElseThrow();
        String invalidToken = "invalid-token-" + System.currentTimeMillis();

        boolean exceptionThrown = false;
        try {
            workerCallbackApplicationService.acceptCallback(new WorkerCallbackRequest(
                    taskId, "worker-callback", invalidToken, "trace-invalid", 
                    TaskStatus.SUCCESS, false, "{\"result\":\"ok\"}", null));
        } catch (Exception e) {
            exceptionThrown = true;
            assertThat(e.getMessage()).containsIgnoringCase("token");
        }

        assertThat(exceptionThrown).isTrue();
        assertThat(schedulerTaskRepository.findByTaskId(taskId).orElseThrow().status())
                .isEqualTo(TaskStatus.DISPATCHED);
    }

    @Test
    void shouldNotOverrideTerminalState() {
        String taskId = "task-terminal-override";
        String requestId = "req-terminal-override";
        setupTaskAndDispatch(taskId, requestId);

        DispatchRecord dispatchRecord = dispatchRecordRepository.findByTaskId(taskId).orElseThrow();
        String dispatchToken = dispatchRecord.dispatchToken();

        workerCallbackApplicationService.acceptCallback(new WorkerCallbackRequest(
                taskId, "worker-callback", dispatchToken, "trace-terminal", 
                TaskStatus.SUCCESS, false, "{\"result\":\"ok\"}", null));

        assertThat(schedulerTaskRepository.findByTaskId(taskId).orElseThrow().status())
                .isEqualTo(TaskStatus.SUCCESS);

        boolean exceptionThrown = false;
        try {
            workerCallbackApplicationService.acceptCallback(new WorkerCallbackRequest(
                    taskId, "worker-callback", dispatchToken, "trace-terminal", 
                    TaskStatus.FAILED, false, null, "should not work"));
        } catch (Exception e) {
            exceptionThrown = true;
        }

        assertThat(exceptionThrown).isTrue();
        assertThat(schedulerTaskRepository.findByTaskId(taskId).orElseThrow().status())
                .isEqualTo(TaskStatus.SUCCESS);
    }

    @Test
    void shouldHandleDuplicateRetryCallback() {
        String taskId = "task-retry-dup";
        String requestId = "req-retry-dup";
        setupTaskAndDispatch(taskId, requestId);

        DispatchRecord dispatchRecord = dispatchRecordRepository.findByTaskId(taskId).orElseThrow();
        String dispatchToken = dispatchRecord.dispatchToken();

        workerCallbackApplicationService.acceptCallback(new WorkerCallbackRequest(
                taskId, "worker-callback", dispatchToken, "trace-retry", 
                TaskStatus.FAILED, true, null, "transient error"));

        assertThat(schedulerTaskRepository.findByTaskId(taskId).orElseThrow().status())
                .isEqualTo(TaskStatus.RETRY_WAIT);

        assertThatNoException().isThrownBy(() ->
            workerCallbackApplicationService.acceptCallback(new WorkerCallbackRequest(
                    taskId, "worker-callback", dispatchToken, "trace-retry", 
                    TaskStatus.FAILED, true, null, "transient error"))
        );

        DispatchRecord updatedRecord = dispatchRecordRepository.findByTaskId(taskId).orElseThrow();
        assertThat(updatedRecord.retryCount()).isEqualTo(1);
    }

    private void setupTaskAndDispatch(String taskId, String requestId) {
        Instant now = Instant.now();
        jdbcTemplate.update("""
                insert into gateway_task(task_id, tenant_id, task_type, biz_key, request_id, worker_group, tag, payload, callback_url, trace_id, status, version, priority, created_at, updated_at)
                values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """,
                taskId, "tenant-callback", "callback-test", "biz-" + taskId, requestId, 
                "callback-group", "default", "{}", "http://callback.test/result", 
                "trace-callback", TaskStatus.QUEUED.name(), 0, 5, Timestamp.from(now), Timestamp.from(now));

        workerRegistrationApplicationService.registerWorker(new WorkerRegisterRequest(
                "worker-callback", "callback-group", "127.0.0.1", 19091, "http", "1.0.0", 
                List.of("callback-test"), List.of("default"), 4, 100));

        pumpIngestApplicationService.acceptSubmittedTask(taskId, "tenant-callback", 
                "callback-test", "callback-group", "trace-callback", 5);

        boolean dispatched = dispatcherApplicationService.dispatchNext();
        assertThat(dispatched).isTrue();
    }
}

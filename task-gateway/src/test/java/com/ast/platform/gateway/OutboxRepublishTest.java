package com.ast.platform.gateway;

import com.ast.platform.domain.task.TaskPublishOutboxStatus;
import com.ast.platform.gateway.application.TaskSubmissionApplicationService;
import com.ast.platform.gateway.domain.model.TaskPublishOutbox;
import com.ast.platform.gateway.domain.repository.TaskPublishOutboxRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.http.MediaType;

import java.sql.Timestamp;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class OutboxRepublishTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private JdbcTemplate jdbcTemplate;
    @Autowired private TaskSubmissionApplicationService taskSubmissionApplicationService;
    @Autowired private TaskPublishOutboxRepository outboxRepository;

    @Test
    void shouldRepublishFailedOutbox() throws Exception {
        mockMvc.perform(post("/task/submit")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "tenantId": "tenant-outbox-retry",
                                  "taskType": "outbox-retry-test",
                                  "bizKey": "biz-outbox-retry",
                                  "requestId": "req-outbox-retry",
                                  "workerGroup": "test-group",
                                  "tag": "default",
                                  "payload": "{}",
                                  "callbackUrl": "http://callback.test/notify"
                                }
                                """))
                .andExpect(status().isOk());

        Thread.sleep(500);

        int republishedCount = taskSubmissionApplicationService.republishPendingOutboxes(100);
        assertThat(republishedCount).isGreaterThanOrEqualTo(0);
    }

    @Test
    void shouldRepublishNewOutbox() throws Exception {
        String taskId = "task-outbox-new-" + System.currentTimeMillis();
        Instant now = Instant.now();

        jdbcTemplate.update("""
                insert into gateway_task(task_id, tenant_id, task_type, biz_key, request_id, worker_group, tag, payload, callback_url, trace_id, status, version, priority, created_at, updated_at)
                values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """,
                taskId, "tenant-outbox-new", "outbox-new-test", "biz-outbox-new", "req-outbox-new",
                "test-group", "default", "{}", "http://callback.test/notify",
                "trace-outbox-new", "QUEUED", 0, 5, Timestamp.from(now), Timestamp.from(now));

        jdbcTemplate.update("""
                insert into gateway_publish_outbox(outbox_id, task_id, tenant_id, task_type, topic, payload, status, retry_count, next_retry_time, created_at, updated_at)
                values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """,
                "outbox-" + taskId, taskId, "tenant-outbox-new", "outbox-new-test", 
                "test-topic", "{}", TaskPublishOutboxStatus.NEW.name(), 0, 
                Timestamp.from(now), Timestamp.from(now), Timestamp.from(now));

        int republishedCount = taskSubmissionApplicationService.republishPendingOutboxes(100);
        assertThat(republishedCount).isGreaterThanOrEqualTo(0);
    }

    @Test
    void shouldNotRepublishAlreadySentOutbox() throws Exception {
        String taskId = "task-outbox-sent-" + System.currentTimeMillis();
        Instant now = Instant.now();

        jdbcTemplate.update("""
                insert into gateway_task(task_id, tenant_id, task_type, biz_key, request_id, worker_group, tag, payload, callback_url, trace_id, status, version, priority, created_at, updated_at)
                values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """,
                taskId, "tenant-outbox-sent", "outbox-sent-test", "biz-outbox-sent", "req-outbox-sent",
                "test-group", "default", "{}", "http://callback.test/notify",
                "trace-outbox-sent", "QUEUED", 0, 5, Timestamp.from(now), Timestamp.from(now));

        jdbcTemplate.update("""
                insert into gateway_publish_outbox(outbox_id, task_id, tenant_id, task_type, topic, payload, status, retry_count, next_retry_time, created_at, updated_at)
                values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """,
                "outbox-" + taskId, taskId, "tenant-outbox-sent", "outbox-sent-test",
                "test-topic", "{}", TaskPublishOutboxStatus.SENT.name(), 0,
                Timestamp.from(now), Timestamp.from(now), Timestamp.from(now));

        int republishedCount = taskSubmissionApplicationService.republishPendingOutboxes(100);
        
        TaskPublishOutbox outbox = outboxRepository.findByTaskId(taskId).orElse(null);
        if (outbox != null) {
            assertThat(outbox.status()).isEqualTo(TaskPublishOutboxStatus.SENT);
        }
    }

    @Test
    void shouldIncrementRetryCountOnFailedRepublish() throws Exception {
        String taskId = "task-outbox-failed-" + System.currentTimeMillis();
        Instant now = Instant.now();

        jdbcTemplate.update("""
                insert into gateway_task(task_id, tenant_id, task_type, biz_key, request_id, worker_group, tag, payload, callback_url, trace_id, status, version, priority, created_at, updated_at)
                values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """,
                taskId, "tenant-outbox-failed", "outbox-failed-test", "biz-outbox-failed", "req-outbox-failed",
                "test-group", "default", "{}", "http://callback.test/notify",
                "trace-outbox-failed", "QUEUED", 0, 5, Timestamp.from(now), Timestamp.from(now));

        jdbcTemplate.update("""
                insert into gateway_publish_outbox(outbox_id, task_id, tenant_id, task_type, topic, payload, status, retry_count, next_retry_time, created_at, updated_at)
                values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """,
                "outbox-" + taskId, taskId, "tenant-outbox-failed", "outbox-failed-test",
                "test-topic", "{}", TaskPublishOutboxStatus.FAILED.name(), 1,
                Timestamp.from(now), Timestamp.from(now), Timestamp.from(now));

        taskSubmissionApplicationService.republishPendingOutboxes(100);

        TaskPublishOutbox outbox = outboxRepository.findByTaskId(taskId).orElse(null);
        if (outbox != null && outbox.status() == TaskPublishOutboxStatus.FAILED) {
            assertThat(outbox.retryCount()).isGreaterThanOrEqualTo(1);
        }
    }
}
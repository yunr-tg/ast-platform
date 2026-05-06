package com.ast.platform.gateway;

import com.ast.platform.contract.gateway.SubmitTaskRequest;
import com.ast.platform.contract.gateway.SubmitTaskResponse;
import com.ast.platform.domain.task.TaskPublishOutboxStatus;
import com.ast.platform.domain.task.TaskStatus;
import com.ast.platform.gateway.application.TaskSubmissionApplicationService;
import com.ast.platform.gateway.domain.model.TaskPublishOutbox;
import com.ast.platform.gateway.domain.repository.TaskPublishOutboxRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(properties = {
        "ast.gateway.mock-send-success=false",
        "spring.datasource.url=jdbc:h2:mem:ast_gateway_failure;MODE=MySQL;DB_CLOSE_DELAY=-1;DATABASE_TO_UPPER=false"
})
class TaskSubmissionFailureTest {

    @Autowired
    private TaskSubmissionApplicationService taskSubmissionApplicationService;

    @Autowired
    private TaskPublishOutboxRepository taskPublishOutboxRepository;

    @Test
    void shouldMarkOutboxFailedAndKeepTaskInitWhenPublishFails() {
        SubmitTaskResponse response = taskSubmissionApplicationService.submitTask(new SubmitTaskRequest(
                "tenant-f",
                "notify-task",
                "biz-f-1",
                "req-f-1",
                "notify-group",
                "default",
                "{\"id\":1}",
                "http://callback.test/failure",
                "trace-f-1",
                5
        ));

        assertThat(response.status()).isEqualTo(TaskStatus.INIT.name());
        assertThat(response.idempotent()).isFalse();
        assertThat(response.outboxStatus()).isEqualTo(TaskPublishOutboxStatus.FAILED.name());

        TaskPublishOutbox outbox = taskPublishOutboxRepository.findByTaskId(response.taskId()).orElseThrow();
        assertThat(outbox.status()).isEqualTo(TaskPublishOutboxStatus.FAILED);
        assertThat(outbox.retryCount()).isEqualTo(1);
        assertThat(outbox.lastErrorMessage()).isEqualTo("mock publish failed");
        assertThat(outbox.nextRetryTime()).isNotNull();
        assertThat(outbox.lastPublishedAt()).isNull();
    }
}

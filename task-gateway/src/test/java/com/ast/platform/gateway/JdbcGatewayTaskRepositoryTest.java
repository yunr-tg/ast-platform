package com.ast.platform.gateway;

import com.ast.platform.common.exception.BusinessException;
import com.ast.platform.common.exception.ErrorCode;
import com.ast.platform.domain.task.TaskStatus;
import com.ast.platform.gateway.domain.model.GatewayTask;
import com.ast.platform.gateway.domain.repository.GatewayTaskRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:ast_gateway_lock;MODE=MySQL;DB_CLOSE_DELAY=-1;DATABASE_TO_UPPER=false"
})
class JdbcGatewayTaskRepositoryTest {

    @Autowired
    private GatewayTaskRepository gatewayTaskRepository;

    @Test
    void shouldRejectStaleVersionUpdate() {
        Instant now = Instant.now();
        GatewayTask task = new GatewayTask(
                "task-lock-1",
                "tenant-lock",
                "render-task",
                "biz-lock",
                "req-lock",
                "render-group",
                "default",
                "{\"a\":1}",
                "http://callback.test/lock",
                "trace-lock",
                TaskStatus.INIT,
                0,
                now,
                now
        );

        gatewayTaskRepository.save(task);
        gatewayTaskRepository.save(task.withStatus(TaskStatus.QUEUED, now.plusSeconds(1)));

        assertThatThrownBy(() -> gatewayTaskRepository.save(task.withStatus(TaskStatus.QUEUED, now.plusSeconds(2))))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.OPTIMISTIC_LOCK_CONFLICT);
    }
}

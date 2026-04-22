package com.ast.platform.gateway.domain.gateway;

import com.ast.platform.gateway.domain.model.TaskPublishOutbox;

public interface TaskMessagePublisher {

    boolean publish(TaskPublishOutbox outbox);
}

package com.ast.platform.gateway.domain.repository;

import com.ast.platform.gateway.domain.model.GatewayTask;

import java.util.Optional;

public interface GatewayTaskRepository {

    Optional<GatewayTask> findByRequestId(String tenantId, String requestId);

    Optional<GatewayTask> findByBizKey(String tenantId, String taskType, String bizKey);

    Optional<GatewayTask> findByTaskId(String taskId);

    GatewayTask save(GatewayTask task);
}

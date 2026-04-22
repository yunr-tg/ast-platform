package com.ast.platform.gateway.infrastructure.repository;

import com.ast.platform.common.exception.BusinessException;
import com.ast.platform.common.exception.ErrorCode;
import com.ast.platform.gateway.domain.model.GatewayTask;
import com.ast.platform.gateway.domain.repository.GatewayTaskRepository;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Repository;

import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

@Repository
@ConditionalOnProperty(prefix = "ast.gateway", name = "storage-type", havingValue = "in-memory")
public class InMemoryGatewayTaskRepository implements GatewayTaskRepository {

    private final Map<String, GatewayTask> taskStore = new ConcurrentHashMap<>();
    private final Map<String, String> requestIndex = new ConcurrentHashMap<>();
    private final Map<String, String> bizKeyIndex = new ConcurrentHashMap<>();

    @Override
    public Optional<GatewayTask> findByRequestId(String tenantId, String requestId) {
        return Optional.ofNullable(requestIndex.get(requestKey(tenantId, requestId)))
                .map(taskStore::get);
    }

    @Override
    public Optional<GatewayTask> findByBizKey(String tenantId, String taskType, String bizKey) {
        return Optional.ofNullable(bizKeyIndex.get(bizKey(tenantId, taskType, bizKey)))
                .map(taskStore::get);
    }

    @Override
    public Optional<GatewayTask> findByTaskId(String taskId) {
        return Optional.ofNullable(taskStore.get(taskId));
    }

    @Override
    public GatewayTask save(GatewayTask task) {
        GatewayTask existing = taskStore.get(task.taskId());
        if (existing != null && task.version() > 0 && existing.version() != task.version() - 1) {
            throw new BusinessException(
                    ErrorCode.OPTIMISTIC_LOCK_CONFLICT,
                    "Gateway task version conflict, taskId=" + task.taskId()
            );
        }
        taskStore.put(task.taskId(), task);
        requestIndex.put(requestKey(task.tenantId(), task.requestId()), task.taskId());
        bizKeyIndex.put(bizKey(task.tenantId(), task.taskType(), task.bizKey()), task.taskId());
        return task;
    }

    private String requestKey(String tenantId, String requestId) {
        return tenantId + "::" + requestId;
    }

    private String bizKey(String tenantId, String taskType, String bizKey) {
        return tenantId + "::" + taskType + "::" + bizKey;
    }
}

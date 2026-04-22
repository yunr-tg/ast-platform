package com.ast.platform.scheduler.infrastructure.gateway;

import com.ast.platform.contract.worker.WorkerDispatchRequest;
import com.ast.platform.contract.worker.WorkerDispatchResponse;
import com.ast.platform.scheduler.domain.gateway.WorkerDispatchGateway;
import com.ast.platform.scheduler.domain.model.ReadyTaskEnvelope;
import com.ast.platform.scheduler.domain.model.SchedulerTaskSnapshot;
import com.ast.platform.scheduler.domain.model.WorkerRuntimeSnapshot;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.time.Instant;

@Component
@ConditionalOnProperty(prefix = "ast.scheduler.worker-dispatch", name = "type", havingValue = "http")
public class HttpWorkerDispatchGateway implements WorkerDispatchGateway {

    private static final Logger log = LoggerFactory.getLogger(HttpWorkerDispatchGateway.class);

    private final RestClient restClient;

    public HttpWorkerDispatchGateway(RestClient.Builder restClientBuilder) {
        this.restClient = restClientBuilder.build();
    }

    @Override
    public boolean dispatch(ReadyTaskEnvelope envelope, SchedulerTaskSnapshot task, WorkerRuntimeSnapshot worker, String dispatchToken) {
        String url = String.format("%s://%s:%d/worker/dispatch", 
                worker.protocol() != null ? worker.protocol() : "http",
                worker.host(), 
                worker.port());
        
        log.info("Dispatching task via HTTP, taskId={}, url={}", envelope.taskId(), url);

        WorkerDispatchRequest request = new WorkerDispatchRequest(
                task.taskId(),
                task.tenantId(),
                task.taskType(),
                task.payload(),
                dispatchToken,
                task.traceId(),
                Instant.now()
        );

        try {
            WorkerDispatchResponse response = restClient.post()
                    .uri(url)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(request)
                    .retrieve()
                    .body(WorkerDispatchResponse.class);

            if (response != null && response.success()) {
                log.info("Successfully dispatched task via HTTP, taskId={}", envelope.taskId());
                return true;
            } else {
                log.warn("Failed to dispatch task via HTTP, taskId={}, response={}", 
                        envelope.taskId(), response);
                return false;
            }
        } catch (Exception e) {
            log.error("Error dispatching task via HTTP, taskId={}, url={}", envelope.taskId(), url, e);
            return false;
        }
    }
}

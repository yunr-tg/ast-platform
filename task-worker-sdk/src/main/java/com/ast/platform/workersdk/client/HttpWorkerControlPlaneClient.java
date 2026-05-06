package com.ast.platform.workersdk.client;

import com.ast.platform.contract.worker.TaskProgressReportRequest;
import com.ast.platform.contract.worker.WorkerCallbackRequest;
import com.ast.platform.contract.worker.WorkerHeartbeatRequest;
import com.ast.platform.contract.worker.WorkerRegisterRequest;
import com.ast.platform.workersdk.config.WorkerSdkProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.client.RestTemplate;

public class HttpWorkerControlPlaneClient implements WorkerControlPlaneClient {

    private static final Logger log = LoggerFactory.getLogger(HttpWorkerControlPlaneClient.class);

    private final RestTemplate restTemplate;
    private final WorkerSdkProperties properties;

    public HttpWorkerControlPlaneClient(RestTemplate restTemplate, WorkerSdkProperties properties) {
        this.restTemplate = restTemplate;
        this.properties = properties;
    }

    @Override
    public void registerWorker(WorkerRegisterRequest request) {
        String url = properties.getSchedulerBaseUrl() + "/worker/register";
        log.info("Registering worker to control plane: {}", url);
        try {
            restTemplate.postForObject(url, request, Void.class);
            log.info("Worker registered successfully: workerId={}", request.workerId());
        } catch (Exception e) {
            log.error("Failed to register worker: {}", e.getMessage());
            throw new RuntimeException("Failed to register worker", e);
        }
    }

    @Override
    public void reportHeartbeat(WorkerHeartbeatRequest request) {
        String url = properties.getSchedulerBaseUrl() + "/worker/heartbeat";
        try {
            restTemplate.postForObject(url, request, Void.class);
            log.debug("Heartbeat reported: workerId={}, status={}", request.workerId(), request.status());
        } catch (Exception e) {
            log.warn("Failed to report heartbeat: {}", e.getMessage());
        }
    }

    @Override
    public void callbackResult(WorkerCallbackRequest request) {
        String url = properties.getSchedulerBaseUrl() + "/worker/callback";
        try {
            restTemplate.postForObject(url, request, Void.class);
            log.info("Callback result sent: taskId={}, status={}", request.taskId(), request.targetStatus());
        } catch (Exception e) {
            log.error("Failed to send callback result: {}", e.getMessage());
            throw new RuntimeException("Failed to send callback result", e);
        }
    }

    @Override
    public void reportProgress(TaskProgressReportRequest request) {
        String url = properties.getSchedulerBaseUrl() + "/worker/progress";
        try {
            restTemplate.postForObject(url, request, Void.class);
            log.debug("Progress reported: taskId={}, progress={}", request.taskId(), request.percentage());
        } catch (Exception e) {
            log.warn("Failed to report progress: {}", e.getMessage());
        }
    }
}

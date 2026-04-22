package com.ast.platform.workersdk.client;

import com.ast.platform.contract.worker.TaskProgressReportRequest;
import com.ast.platform.contract.worker.WorkerCallbackRequest;
import com.ast.platform.contract.worker.WorkerHeartbeatRequest;
import com.ast.platform.contract.worker.WorkerRegisterRequest;
import com.ast.platform.workersdk.config.WorkerSdkProperties;
import org.springframework.web.client.RestClient;

public class DefaultWorkerControlPlaneClient implements WorkerControlPlaneClient {

    private final RestClient restClient;

    public DefaultWorkerControlPlaneClient(RestClient.Builder builder, WorkerSdkProperties properties) {
        this.restClient = builder.baseUrl(properties.getSchedulerBaseUrl()).build();
    }

    @Override
    public void registerWorker(WorkerRegisterRequest request) {
        restClient.post().uri("/worker/register").body(request).retrieve().toBodilessEntity();
    }

    @Override
    public void reportHeartbeat(WorkerHeartbeatRequest request) {
        restClient.post().uri("/worker/heartbeat").body(request).retrieve().toBodilessEntity();
    }

    @Override
    public void callbackResult(WorkerCallbackRequest request) {
        restClient.post().uri("/worker/callback").body(request).retrieve().toBodilessEntity();
    }

    @Override
    public void reportProgress(TaskProgressReportRequest request) {
        restClient.post().uri("/worker/progress").body(request).retrieve().toBodilessEntity();
    }
}

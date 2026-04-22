package com.ast.platform.workersdk.client;

import com.ast.platform.contract.worker.TaskProgressReportRequest;
import com.ast.platform.contract.worker.WorkerCallbackRequest;
import com.ast.platform.contract.worker.WorkerHeartbeatRequest;
import com.ast.platform.contract.worker.WorkerRegisterRequest;

public interface WorkerControlPlaneClient {

    void registerWorker(WorkerRegisterRequest request);

    void reportHeartbeat(WorkerHeartbeatRequest request);

    void callbackResult(WorkerCallbackRequest request);

    void reportProgress(TaskProgressReportRequest request);
}

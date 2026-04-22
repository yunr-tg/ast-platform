package com.ast.platform.scheduler.controller;

import com.ast.platform.common.response.BaseResponse;
import com.ast.platform.contract.worker.WorkerHeartbeatRequest;
import com.ast.platform.contract.worker.WorkerRegisterRequest;
import com.ast.platform.scheduler.application.WorkerHeartbeatApplicationService;
import com.ast.platform.scheduler.application.WorkerRegistrationApplicationService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class WorkerController {

    private final WorkerRegistrationApplicationService registrationService;
    private final WorkerHeartbeatApplicationService heartbeatService;

    public WorkerController(WorkerRegistrationApplicationService registrationService,
                            WorkerHeartbeatApplicationService heartbeatService) {
        this.registrationService = registrationService;
        this.heartbeatService = heartbeatService;
    }

    @PostMapping("/worker/register")
    public BaseResponse<Void> register(@Valid @RequestBody WorkerRegisterRequest request) {
        registrationService.registerWorker(request);
        return BaseResponse.success(null);
    }

    @PostMapping("/worker/heartbeat")
    public BaseResponse<Void> heartbeat(@Valid @RequestBody WorkerHeartbeatRequest request) {
        heartbeatService.reportHeartbeat(request);
        return BaseResponse.success(null);
    }
}

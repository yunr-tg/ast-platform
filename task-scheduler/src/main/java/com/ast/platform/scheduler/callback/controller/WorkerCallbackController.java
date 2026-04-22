package com.ast.platform.scheduler.callback.controller;

import org.slf4j.MDC;
import com.ast.platform.common.response.BaseResponse;
import com.ast.platform.contract.worker.WorkerCallbackRequest;
import com.ast.platform.scheduler.callback.application.WorkerCallbackApplicationService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class WorkerCallbackController {

    private final WorkerCallbackApplicationService applicationService;

    public WorkerCallbackController(WorkerCallbackApplicationService applicationService) {
        this.applicationService = applicationService;
    }

    @PostMapping("/worker/callback")
    public BaseResponse<Void> callback(@Valid @RequestBody WorkerCallbackRequest request) {
        // Micrometer handles HTTP trace, but we ensure business traceId is in MDC
        MDC.put("traceId", request.traceId());
        try {
            applicationService.acceptCallback(request);
            return BaseResponse.success(null);
        } finally {
            MDC.remove("traceId");
        }
    }
}

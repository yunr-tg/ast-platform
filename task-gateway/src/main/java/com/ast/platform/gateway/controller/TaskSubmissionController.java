package com.ast.platform.gateway.controller;

import com.ast.platform.contract.gateway.CancelTaskRequest;
import com.ast.platform.common.response.BaseResponse;
import com.ast.platform.contract.gateway.SubmitTaskRequest;
import com.ast.platform.contract.gateway.SubmitTaskResponse;
import com.ast.platform.gateway.application.TaskSubmissionApplicationService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/task")
public class TaskSubmissionController {

    private final TaskSubmissionApplicationService applicationService;

    public TaskSubmissionController(TaskSubmissionApplicationService applicationService) {
        this.applicationService = applicationService;
    }

    @PostMapping("/submit")
    public BaseResponse<SubmitTaskResponse> submit(@Valid @RequestBody SubmitTaskRequest request) {
        return BaseResponse.success(applicationService.submitTask(request));
    }

    @PostMapping("/cancel")
    public BaseResponse<Void> cancel(@Valid @RequestBody CancelTaskRequest request) {
        applicationService.cancelTask(request);
        return BaseResponse.success(null);
    }
}

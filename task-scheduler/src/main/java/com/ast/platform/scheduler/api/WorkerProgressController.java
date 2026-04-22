package com.ast.platform.scheduler.api;

import com.ast.platform.contract.worker.TaskProgressReportRequest;
import com.ast.platform.scheduler.application.ProgressApplicationService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/worker")
public class WorkerProgressController {

    private final ProgressApplicationService progressApplicationService;

    public WorkerProgressController(ProgressApplicationService progressApplicationService) {
        this.progressApplicationService = progressApplicationService;
    }

    @PostMapping("/progress")
    public void reportProgress(@RequestBody @Valid TaskProgressReportRequest request) {
        progressApplicationService.handleProgressReport(request);
    }
}

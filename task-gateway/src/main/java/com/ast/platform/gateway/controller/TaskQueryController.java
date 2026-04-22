package com.ast.platform.gateway.controller;

import com.ast.platform.contract.gateway.TaskProgressResponse;
import com.ast.platform.domain.task.TaskProgressRepository;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/task")
public class TaskQueryController {

    private final TaskProgressRepository taskProgressRepository;

    public TaskQueryController(TaskProgressRepository taskProgressRepository) {
        this.taskProgressRepository = taskProgressRepository;
    }

    @GetMapping("/{taskId}/progress")
    public ResponseEntity<TaskProgressResponse> getProgress(@PathVariable String taskId) {
        return taskProgressRepository.getLatestProgress(taskId)
                .map(p -> new TaskProgressResponse(
                        p.taskId(),
                        p.percentage(),
                        p.message(),
                        p.payload(),
                        p.timestamp()
                ))
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }
}

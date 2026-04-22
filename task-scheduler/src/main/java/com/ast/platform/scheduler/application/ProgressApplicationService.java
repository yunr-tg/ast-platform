package com.ast.platform.scheduler.application;

import com.ast.platform.contract.worker.TaskProgressReportRequest;
import com.ast.platform.domain.task.TaskProgress;
import com.ast.platform.domain.task.TaskProgressRepository;
import io.micrometer.tracing.Span;
import io.micrometer.tracing.Tracer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

@Service
public class ProgressApplicationService {

    private static final Logger log = LoggerFactory.getLogger(ProgressApplicationService.class);

    private final TaskProgressRepository taskProgressRepository;
    private final ExecutorService executorService;
    private final Tracer tracer;

    public ProgressApplicationService(TaskProgressRepository taskProgressRepository, Tracer tracer) {
        this.taskProgressRepository = taskProgressRepository;
        this.tracer = tracer;
        // Decouple progress processing from HTTP thread
        this.executorService = Executors.newFixedThreadPool(Runtime.getRuntime().availableProcessors() * 2);
    }

    public void handleProgressReport(TaskProgressReportRequest report) {
        // Quick handoff to async processor
        executorService.submit(() -> {
            Span span = tracer.nextSpan().name("process-progress").start();
            try (Tracer.SpanInScope ws = tracer.withSpan(span)) {
                log.debug("Processing progress report: task={}, %={}", report.taskId(), report.percentage());
                
                TaskProgress domainProgress = new TaskProgress(
                        report.taskId(),
                        report.percentage(),
                        report.message(),
                        report.payload(),
                        report.timestamp()
                );
                
                taskProgressRepository.saveProgress(domainProgress, report.sequence());
            } catch (Exception e) {
                log.error("Error processing progress report for task {}", report.taskId(), e);
            } finally {
                span.end();
            }
        });
    }
}

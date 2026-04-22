package com.ast.platform.workersdk.handler;

import com.ast.platform.contract.worker.TaskProgressReportRequest;
import com.ast.platform.workersdk.client.WorkerControlPlaneClient;
import com.ast.platform.workersdk.config.WorkerSdkProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Intelligent progress reporter with throttling logic.
 */
public class ProgressReporter {

    private static final Logger log = LoggerFactory.getLogger(ProgressReporter.class);

    private final String taskId;
    private final WorkerControlPlaneClient client;
    private final WorkerSdkProperties properties;

    private final AtomicLong lastReportTime = new AtomicLong(0);
    private final AtomicLong lastReportPercentage = new AtomicLong(-1);
    private final AtomicLong sequence = new AtomicLong(0);

    // Throttling thresholds
    private static final long MIN_INTERVAL_MS = 2000;
    private static final int MIN_DELTA_PERCENTAGE = 1;

    public ProgressReporter(String taskId, WorkerControlPlaneClient client, WorkerSdkProperties properties) {
        this.taskId = taskId;
        this.client = client;
        this.properties = properties;
    }

    public void report(int percentage, String message, Map<String, Object> payload) {
        long now = System.currentTimeMillis();
        long lastTime = lastReportTime.get();
        long lastPercentage = lastReportPercentage.get();

        // Throttling logic:
        // 1. Always report if it's the first report (lastPercentage == -1)
        // 2. Always report if it's 0% or 100%
        // 3. Report if delta > 1% AND time interval > 2s
        boolean shouldReport = lastPercentage == -1 
                || percentage == 0 
                || percentage == 100
                || (Math.abs(percentage - lastPercentage) >= MIN_DELTA_PERCENTAGE && (now - lastTime) >= MIN_INTERVAL_MS);

        if (shouldReport) {
            try {
                client.reportProgress(new TaskProgressReportRequest(
                        taskId,
                        properties.getWorkerId(),
                        percentage,
                        message,
                        payload,
                        sequence.incrementAndGet(),
                        now
                ));
                lastReportTime.set(now);
                lastReportPercentage.set(percentage);
            } catch (Exception e) {
                log.warn("Failed to report progress for task {}: {}", taskId, e.getMessage());
            }
        }
    }
}

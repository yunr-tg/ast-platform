package com.ast.platform.scheduler.retry;

import com.ast.platform.scheduler.retry.application.RetryApplicationService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class RetryMoverJob {

    private static final Logger log = LoggerFactory.getLogger(RetryMoverJob.class);
    private final RetryApplicationService retryApplicationService;

    public RetryMoverJob(RetryApplicationService retryApplicationService) {
        this.retryApplicationService = retryApplicationService;
    }

    @Scheduled(fixedDelayString = "${ast.scheduler.retry-fixed-delay-ms:1000}")
    public void moveRetryTasks() {
        int moved = retryApplicationService.moveRetryTasks();
        if (moved > 0) {
            log.info("retry mover moved tasks, count={}", moved);
        }
    }
}

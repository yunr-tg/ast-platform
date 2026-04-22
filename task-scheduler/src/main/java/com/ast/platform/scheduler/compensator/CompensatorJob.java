package com.ast.platform.scheduler.compensator;

import com.ast.platform.scheduler.compensator.application.CompensatorApplicationService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(prefix = "ast.scheduler.compensator", name = "enabled", havingValue = "true")
public class CompensatorJob {

    private static final Logger log = LoggerFactory.getLogger(CompensatorJob.class);
    private final CompensatorApplicationService compensatorApplicationService;

    public CompensatorJob(CompensatorApplicationService compensatorApplicationService) {
        this.compensatorApplicationService = compensatorApplicationService;
    }

    @Scheduled(fixedDelayString = "${ast.scheduler.compensator.fixed-delay-ms:30000}")
    public void compensate() {
        compensatorApplicationService.compensate();
        log.debug("compensator tick executed");
    }
}

package com.ast.platform.gateway.application;

import com.ast.platform.gateway.config.GatewayProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class GatewayOutboxRepublisherJob {

    private static final Logger log = LoggerFactory.getLogger(GatewayOutboxRepublisherJob.class);

    private final TaskSubmissionApplicationService taskSubmissionApplicationService;
    private final GatewayProperties gatewayProperties;

    public GatewayOutboxRepublisherJob(TaskSubmissionApplicationService taskSubmissionApplicationService,
                                       GatewayProperties gatewayProperties) {
        this.taskSubmissionApplicationService = taskSubmissionApplicationService;
        this.gatewayProperties = gatewayProperties;
    }

    @Scheduled(fixedDelayString = "${ast.gateway.outbox-republish-fixed-delay-ms:3000}")
    public void republishPendingOutboxes() {
        int publishedCount = taskSubmissionApplicationService.republishPendingOutboxes(
                gatewayProperties.getOutboxBatchSize()
        );
        if (publishedCount > 0) {
            log.info("gateway outbox republish finished, publishedCount={}", publishedCount);
        }
    }
}

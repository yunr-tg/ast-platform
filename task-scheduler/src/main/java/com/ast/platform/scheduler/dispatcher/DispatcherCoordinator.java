package com.ast.platform.scheduler.dispatcher;

import com.ast.platform.scheduler.dispatcher.application.DispatcherApplicationService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(prefix = "ast.scheduler", name = "dispatcher-enabled", havingValue = "true")
public class DispatcherCoordinator {

    private static final Logger log = LoggerFactory.getLogger(DispatcherCoordinator.class);
    private final DispatcherApplicationService dispatcherApplicationService;

    public DispatcherCoordinator(DispatcherApplicationService dispatcherApplicationService) {
        this.dispatcherApplicationService = dispatcherApplicationService;
    }

    @Scheduled(fixedDelayString = "${ast.scheduler.dispatcher-fixed-delay-ms:1000}")
    public void dispatchNext() {
        boolean dispatched = dispatcherApplicationService.dispatchNext();
        if (dispatched) {
            log.info("dispatcher executed one task");
        }
    }
}

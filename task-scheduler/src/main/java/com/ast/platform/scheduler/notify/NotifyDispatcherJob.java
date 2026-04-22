package com.ast.platform.scheduler.notify;

import com.ast.platform.scheduler.notify.application.NotifyApplicationService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(prefix = "ast.scheduler.notify", name = "enabled", havingValue = "true")
public class NotifyDispatcherJob {

    private static final Logger log = LoggerFactory.getLogger(NotifyDispatcherJob.class);
    private final NotifyApplicationService notifyApplicationService;

    public NotifyDispatcherJob(NotifyApplicationService notifyApplicationService) {
        this.notifyApplicationService = notifyApplicationService;
    }

    @Scheduled(fixedDelayString = "${ast.scheduler.notify.fixed-delay-ms:5000}")
    public void dispatchNotify() {
        int sent = notifyApplicationService.dispatchNotify();
        if (sent > 0) {
            log.info("notify dispatcher sent callbacks, count={}", sent);
        }
    }
}

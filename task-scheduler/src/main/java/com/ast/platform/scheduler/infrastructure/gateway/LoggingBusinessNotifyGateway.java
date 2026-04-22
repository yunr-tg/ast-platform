package com.ast.platform.scheduler.infrastructure.gateway;

import com.ast.platform.scheduler.domain.gateway.BusinessNotifyGateway;
import com.ast.platform.scheduler.domain.model.NotifyOutboxRecord;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

@Component
public class LoggingBusinessNotifyGateway implements BusinessNotifyGateway {

    private static final Logger log = LoggerFactory.getLogger(LoggingBusinessNotifyGateway.class);

    @Override
    public boolean notify(NotifyOutboxRecord record) {
        log.info("notify business skeleton, outboxId={}, taskId={}, callbackUrl={}",
                record.outboxId(), record.taskId(), record.callbackUrl());
        return true;
    }
}
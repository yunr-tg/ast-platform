package com.ast.platform.scheduler.domain.gateway;

import com.ast.platform.scheduler.domain.model.NotifyOutboxRecord;

public interface BusinessNotifyGateway {

    boolean notify(NotifyOutboxRecord record);
}
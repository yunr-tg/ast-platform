package com.ast.platform.scheduler.domain.gateway;

import com.ast.platform.scheduler.domain.model.ReadyTaskEnvelope;
import com.ast.platform.scheduler.domain.model.SchedulerTaskSnapshot;
import com.ast.platform.scheduler.domain.model.WorkerRuntimeSnapshot;

public interface WorkerDispatchGateway {

    boolean dispatch(ReadyTaskEnvelope envelope, SchedulerTaskSnapshot task, WorkerRuntimeSnapshot worker, String dispatchToken);
}
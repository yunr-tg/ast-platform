package com.ast.platform.scheduler.infrastructure.gateway;

import com.ast.platform.scheduler.domain.gateway.WorkerDispatchGateway;
import com.ast.platform.scheduler.domain.model.ReadyTaskEnvelope;
import com.ast.platform.scheduler.domain.model.SchedulerTaskSnapshot;
import com.ast.platform.scheduler.domain.model.WorkerRuntimeSnapshot;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(prefix = "ast.scheduler.worker-dispatch", name = "type", havingValue = "logging", matchIfMissing = true)
public class LoggingWorkerDispatchGateway implements WorkerDispatchGateway {

    private static final Logger log = LoggerFactory.getLogger(LoggingWorkerDispatchGateway.class);

    @Override
    public boolean dispatch(ReadyTaskEnvelope envelope, SchedulerTaskSnapshot task, WorkerRuntimeSnapshot worker, String dispatchToken) {
        log.info("dispatch task skeleton, taskId={}, workerId={}, workerGroup={}, dispatchToken={}",
                envelope.taskId(), worker.workerId(), worker.workerGroup(), dispatchToken);
        return true;
    }
}
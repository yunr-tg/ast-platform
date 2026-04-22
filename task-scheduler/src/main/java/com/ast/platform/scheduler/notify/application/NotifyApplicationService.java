package com.ast.platform.scheduler.notify.application;

import io.micrometer.tracing.Tracer;
import io.micrometer.tracing.Span;
import org.slf4j.MDC;
import com.ast.platform.scheduler.domain.gateway.BusinessNotifyGateway;
import com.ast.platform.scheduler.domain.model.NotifyOutboxRecord;
import com.ast.platform.scheduler.domain.repository.NotifyOutboxRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.Instant;

@Service
public class NotifyApplicationService {

    private static final Logger log = LoggerFactory.getLogger(NotifyApplicationService.class);

    private final NotifyOutboxRepository notifyOutboxRepository;
    private final BusinessNotifyGateway businessNotifyGateway;
    private final Tracer tracer;

    public NotifyApplicationService(NotifyOutboxRepository notifyOutboxRepository,
                                    BusinessNotifyGateway businessNotifyGateway,
                                    Tracer tracer) {
        this.notifyOutboxRepository = notifyOutboxRepository;
        this.businessNotifyGateway = businessNotifyGateway;
        this.tracer = tracer;
    }

    public int dispatchNotify() {
        int successCount = 0;
        for (NotifyOutboxRecord record : notifyOutboxRepository.findDueRecords("NEW", Instant.now(), 100)) {
            Span span = tracer.nextSpan().name("dispatch-notify").start();
            try (Tracer.SpanInScope ws = tracer.withSpan(span)) {
                MDC.put("traceId", record.traceId());
                boolean success = businessNotifyGateway.notify(record);
                NotifyOutboxRecord next = success
                        ? record.withStatus("SENT", null, null, Instant.now())
                        : record.withStatus("FAILED", "notify failed", Instant.now().plusSeconds(5), Instant.now());
                notifyOutboxRepository.save(next);
                if (success) {
                    successCount++;
                }
            } finally {
                span.end();
                MDC.remove("traceId");
            }
        }
        if (successCount > 0) {
            log.info("notify outbox sent, count={}", successCount);
        }
        return successCount;
    }
}
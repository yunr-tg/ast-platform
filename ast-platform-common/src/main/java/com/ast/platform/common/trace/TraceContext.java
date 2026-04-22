package com.ast.platform.common.trace;

import io.micrometer.tracing.Tracer;
import io.micrometer.tracing.Span;
import org.slf4j.MDC;
import java.util.UUID;

public final class TraceContext {

    public static final String TRACE_ID_KEY = "traceId";

    private TraceContext() {
    }

    public static String newTraceId() {
        return UUID.randomUUID().toString().replace("-", "");
    }

    /**
     * @deprecated Use Micrometer Tracing directly or inject Tracer.
     * This method is kept for backward compatibility during transition.
     */
    @Deprecated
    public static void setTraceId(String traceId) {
        if (traceId != null) {
            MDC.put(TRACE_ID_KEY, traceId);
        }
    }

    /**
     * @deprecated Use Micrometer Tracing directly.
     */
    @Deprecated
    public static String getTraceId() {
        return MDC.get(TRACE_ID_KEY);
    }

    /**
     * @deprecated Use Micrometer Tracing directly.
     */
    @Deprecated
    public static void clear() {
        MDC.remove(TRACE_ID_KEY);
    }
}

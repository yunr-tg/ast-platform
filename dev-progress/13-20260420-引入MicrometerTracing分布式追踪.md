# 引入 Micrometer Tracing 分布式追踪

## 本次进展

- **框架选型**：引入了 **Micrometer Tracing + OpenTelemetry (OTel)** 作为标准的分布式追踪方案，替换了原有的手动 MDC 透传方案。
- **全链路自动传播**：
    - **HTTP 传播**：利用 Micrometer 的 `RestClient` 拦截器和 `WebMvc` 过滤器，实现了从调度器到 Worker SDK 的自动 Trace 上下文传播。
    - **日志增强**：统一配置了日志 Pattern `[%X{traceId:-},%X{spanId:-}]`，确保所有模块的日志都能自动带上追踪 ID。
    - **跨异步边界**：在 `task-scheduler` 的调度循环、MQ 消费逻辑以及 `task-worker-sdk` 的线程池执行逻辑中，通过 `Tracer` 手动管理 Span 声明周期，确保护链路不中断。
- **状态机健壮性升级**：
    - 更新了 `TaskStatus`，允许任务从 `DISPATCHED` 直接流转至终端状态（`SUCCESS`/`FAILED`），以支持更灵活的 Worker 回调场景。
    - 升级了 `WorkerCallbackApplicationService` 的幂等性，支持基于当前实际状态进行流转。
- **验证**：修复了所有受影响的集成测试，并确保整仓 `mvn test` 全部通过。

## 当前状态

- 平台具备了工业级的分布式追踪能力。
- 业务日志与调度日志通过 `traceId` 强关联，支持全链路排查。
- 系统架构符合 Spring Boot 3 官方推荐的可观测性最佳实践。

## 下一步建议

- **接入可视化后端**：建议在 P1 阶段引入 **Zipkin** 或 **Jaeger**，通过 UI 查看链路瀑布图。
- **接入 Prometheus Metrics**：利用 `Micrometer Observation` 同时生成 Traces 和 Metrics，实现监控闭环。
- **完善死信处理**：针对 `Compensator` 扫描出的长期超时任务，建立完善的归档与报警机制。

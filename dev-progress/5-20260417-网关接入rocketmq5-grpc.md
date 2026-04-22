# 网关接入 RocketMQ5 gRPC

## 本次进展

- 为 `task-gateway` 引入 `rocketmq-client-java` 依赖
- 新增 `ast.gateway.rocketmq.*` 强类型配置
- 新增 RocketMQ 5 gRPC 版 Producer Bean 装配
- 新增基于 `ClientServiceProvider` 和 `Producer.send(...)` 的真实消息发布器
- 保留日志型发布器作为默认关闭 RocketMQ 时的回退实现
- 增加发布器按配置切换逻辑，默认关闭 RocketMQ 接入
- 完成整仓 `mvn test` 验证，确保现有网关链路不受影响

## 当前状态

- 网关已具备 RocketMQ 5 gRPC Java SDK 接入能力
- 当前默认 `ast.gateway.rocketmq.enabled=false`
- 打开配置并提供 Proxy `endpoints` 后，可切换为真实 RocketMQ 发送

## 下一步建议

- 增加基于真实发送异常的失败原因映射
- 将 `sendReceipt.getMessageId()` 持久化到发送审计字段
- 增加本地 Docker RocketMQ 联调脚本与集成测试

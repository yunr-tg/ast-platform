# task-scheduler 功能开发

## 本次进展

- 为 `task-scheduler` 接入 JDBC、H2/MySQL 兼容数据源与初始化表结构
- 新增 Worker 运行态、派发记录、通知外盒、补偿审计等基础表
- 新增调度侧任务快照、Worker 运行态、派发记录、通知外盒等领域模型
- 实现 Worker 注册与心跳入库
- 实现本地调度队列骨架，支持 ready/retry 回灌
- 实现 Dispatcher 最小编排：选取可派发 Worker、生成 `dispatchToken`、推进任务到 `RUNNING`
- 实现 Callback 状态推进：校验 `dispatchToken`、推进 `SUCCESS/RETRY_WAIT`、生成通知外盒
- 实现 Retry 任务回灌到 ready queue
- 实现 Notify 外盒最小发送骨架
- 为调度模块补充集成测试，覆盖 Worker 控制面、派发成功回调、重试回灌
- 完成整仓 `mvn test` 验证

## 当前状态

- `task-scheduler` 已从纯日志空壳推进到可运行的最小闭环
- 当前 `pump` 仍未接入真实 RocketMQ 消费
- 当前调度队列仍为本地内存实现，后续需要替换为 Redis 逻辑队列
- 当前 Worker 派发和业务通知仍为日志型网关实现

## 下一步建议

- 将本地调度队列替换为 Redis `ready/processing/retry/active:keys`
- 接入 RocketMQ 5 gRPC Consumer 实现真实 Pump
- 接入真实 Worker 派发协议和 ACK/运行态推进
- 补充 Notify 失败重试上限、死信与补偿审计

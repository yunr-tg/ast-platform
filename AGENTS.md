# AST Platform AI 开发约束

## 1. 用途

本文件只保留会直接影响 AI 写代码的硬规则。
如与代码实现冲突，以本文件为准；如与正式设计文档冲突，以更新后的设计文档为准。

设计基线文档：

- `design-doc/系统架构方案.md`
- `design-doc/03-架构设计/21-系统架构设计文档.md`
- `design-doc/03-架构设计/22-技术选型报告.md`
- `design-doc/03-架构设计/23-部署架构设计.md`
- `design-doc/03-架构设计/24-数据架构设计.md`
- `design-doc/03-架构设计/25-核心业务领域模型-DDD.md`

## 2. 当前阶段

当前固定为 `P0`。

本期在做：

- `task-gateway`
- `task-scheduler`
- `task-worker-sdk`
- `ast-platform-common`
- `ast-platform-domain`
- `ast-platform-api-contract`
- `ast-platform-infra`

本期不做：

- `task-admin`
- 租户差异化治理，如 VIP、配额、优先级
- `Nacos Discovery` 注册中心
- `RocketMQ 5.x LiteTopic`
- DAG、复杂工作流

本期允许：

- 接入 `Nacos Config` 作为配置中心

## 3. 架构红线

固定架构：

- 接入层：`Gateway`
- 一级缓冲层：`RocketMQ 标准版`
- 二级逻辑调度层：`Pump + Dispatcher + Redis`
- 事实存储层：`MySQL 8.0`
- 执行层：`Worker SDK + Worker Group`
- 可观测层：`Prometheus + Grafana + ELK/Loki`

必须遵守：

- 核心隔离维度固定为 `tenantId + taskType`
- 当前阶段所有租户同权
- 最终调度顺序只能由 `Dispatcher` 裁决
- 允许重复，不允许静默丢失
- 调度必须基于 Worker 实时运行态
- 接入、缓冲、调度、执行、回调、补偿必须解耦

## 4. 模块与职责

### 4.1 依赖模块

- `ast-platform-common`
  - 公共异常、错误码、常量、工具、统一响应、追踪基础能力
- `ast-platform-domain`
  - 聚合、值对象、状态机规则、领域服务接口
- `ast-platform-api-contract`
  - DTO/VO、接口协议、Worker 注册/心跳/回调协议
- `ast-platform-infra`
  - MySQL、Redis、RocketMQ、HTTP/gRPC、监控埋点等基础设施适配
- `task-worker-sdk`
  - 给业务 Worker 使用的注册、心跳、任务处理、结果回调 SDK

规则：

- 依赖模块不单独启动
- 不包含部署入口和运行时启动逻辑

### 4.2 启动项目

- `task-gateway`
  - 统一接入入口；提交任务、参数校验、幂等校验、任务落库、入队外盒、发送 RocketMQ
- `task-scheduler`
  - 调度与状态推进中枢；负责 `pump`、`dispatcher`、`retry`、`callback`、`notify`、`compensator`

规则：

- 启动项目拥有 `Application` 启动类
- 启动项目之间不得直接强依赖

### 4.3 task-scheduler 内部子系统

- `pump`
  - 从 RocketMQ 消费并搬运到 Redis ready queue
- `dispatcher`
  - 公平调度、Worker 选点、任务派发
- `retry`
  - 重试任务回灌、重试窗口推进
- `callback`
  - 接收 Worker 结果回调并推进任务状态机
- `notify`
  - 异步通知业务方、通知重试、通知死信
- `compensator`
  - 异常任务扫描、对账、超时回收、补偿回灌

规则：

- 以上子系统必须按包隔离
- 当前可同进程部署，但必须方便后续拆成独立启动项目
- 禁止写成一个“大调度器实现类”

## 5. 分层与依赖

推荐分层：

- `controller` / `api`
- `application`
- `domain`
- `infrastructure`
- `config`

职责：

- `controller/api`：协议转换
- `application`：用例编排、事务边界
- `domain`：业务规则、聚合、不变量
- `infrastructure`：数据库、MQ、Redis、外部系统接入
- `config`：Spring Boot 配置和 Bean 装配

依赖方向：

- `domain` 可依赖 `common`
- `api-contract` 可依赖 `common`
- `infra` 可依赖 `common`
- 启动项目可依赖 `common + domain + api-contract + infra`
- `task-worker-sdk` 可依赖 `common + api-contract`

禁止：

- `domain` 依赖启动项目
- 一个启动项目直接依赖另一个启动项目
- `api-contract` 依赖 `infra`
- `common` 依赖业务模块
- Controller 直接操作 Mapper/Repository 实现
- 把复杂逻辑写进 MQ Listener、定时任务、配置类

## 6. 状态机与一致性

任务状态机：

```text
INIT -> QUEUED -> DISPATCHED -> RUNNING -> SUCCESS
 |        |           |            \-> RETRY_WAIT -> QUEUED
 |        |           |            \-> FAILED
 |        |           |            \-> DEAD_LETTER
 \-> CANCELLED        \-> CANCELLED
```

强制规则：

- 所有状态推进必须幂等
- 状态更新必须带条件，如 `version` 或 `expectedStatus`
- 所有终态不可覆盖
- 可重试失败只能进入 `RETRY_WAIT`
- 回调改写状态前必须校验 `dispatchToken`
- 允许重复派发，但不得破坏终态安全

一致性规则：

- 任务提交必须有入队外盒
- 业务通知必须有回调外盒
- “DB 成功但 MQ 失败”必须可恢复
- “平台收到 Worker 结果但通知业务方失败”必须可恢复
- 平台只追求最终一致性，不做分布式强事务

核心原则：

- 允许重复
- 不允许静默丢失
- 允许延迟
- 不允许终态混乱

## 7. 调度与数据边界

### 7.1 Redis

Redis 不是事实库，只承载：

- ready / processing / retry 队列
- active keys
- Worker 元数据
- Worker 最新运行态

标准 Key：

- `dispatch:ready:{tenantId}:{taskType}`
- `dispatch:processing:{tenantId}:{taskType}`
- `dispatch:retry:{tenantId}:{taskType}`
- `dispatch:active:keys`
- `ats:worker:group:{workerGroup}`
- `ats:worker:meta:{workerId}`
- `ats:worker:runtime:{workerId}`

规则：

- Redis 不存大 payload
- `ready -> processing` 必须原子迁移，优先 Lua

### 7.2 MySQL

MySQL 是最终事实源，承载：

- 任务事实表
- 状态机流转
- 入队外盒
- 业务回调外盒
- 回调日志
- 死信
- 补偿审计

规则：

- 任务落库必须先于 RocketMQ 投递成功语义
- 所有补偿动作必须有持久化记录
- 不得依赖纯内存补偿

### 7.3 RocketMQ

RocketMQ 只做一级缓冲：

- 新任务优先进入 RocketMQ
- `Pump` 从 RocketMQ 消费后写 Redis
- 不负责最终公平调度

禁止：

- 不得基于 `RocketMQ 5.x LiteTopic` 落地
- 不得实现“MQ 直接消费即最终派发”

## 8. Worker 与 Nacos

Worker 治理基线：

- Worker 通过平台注册
- Worker 周期性上报心跳
- 平台把最新运行态写入 Redis
- `Dispatcher` 基于 Redis 运行态选点

必备接口：

- `POST /worker/register`
- `POST /worker/heartbeat`

最少心跳字段：

- `workerId`
- `workerGroup`
- `supportedTaskTypes`
- `status`
- `activeTaskCount`
- `maxConcurrency`
- `availableSlots`
- `avgRt`
- `errorRate`

Worker 状态：

- `UP`
- `DEGRADED`
- `DRAINING`
- `DOWN`
- `BLOCKED`

Nacos 规则：

- 允许 `Nacos Config`
- 不接入 `Nacos Discovery`
- 控制面服务发现优先 `Kubernetes Service + DNS` 或固定配置
- Worker 发现不能简化为普通服务注册发现

## 9. 命名、配置、观测、测试

命名：

- 模块名用小写 `kebab-case`
- 公共模块用 `ast-platform-*`
- 启动项目用 `task-*`
- 方法名使用明确业务动词，如 `submitTask`、`dispatchNext`

配置：

- 所有运行时参数必须外置化
- 使用强类型配置类
- 禁止硬编码地址、Topic、阈值、密钥
- 推荐前缀：
  - `ast.gateway.*`
  - `ast.scheduler.*`
  - `ast.worker-sdk.*`
  - `ast.scheduler.callback.*`
  - `ast.scheduler.notify.*`
  - `ast.scheduler.compensator.*`

可观测：

- 必须有结构化日志、`traceId`、关键业务指标
- 至少覆盖：提交 TPS、入队成功率、调度延迟、执行耗时、重试数、死信数、Redis 队列积压、RocketMQ 堆积、Worker 心跳成功率、业务回调成功率

测试：

- 必补：状态机规则、幂等关键链路
- 优先补：重复回调、外盒重发表、Lua 原子迁移、Worker 选点逻辑
- 禁止堆低价值 getter/setter 测试

## 10. 明确禁止事项

除非设计文档明确更新，否则禁止：

- 在 `P0` 引入租户优先级、配额、VIP 调度
- 把 Redis 当最终事实库
- 用 MySQL 轮询作为主调度队列
- 让 MQ 决定最终公平调度顺序
- 引入 `Nacos Discovery` 等注册中心
- 把 `callback`、`notify`、`compensator` 写成不可拆的大类
- 跨子系统直接调用私有实现类
- 将 Worker 发现简化为普通服务注册发现

## 11. AI 执行清单

开发前：

- 确认目标模块和限界上下文
- 确认是否属于 `P0`
- 确认是否影响状态机、幂等和边界
- 确认本次进度记录是否需要写入 `dev-progress`

开发中：

- 保持分层清晰
- 配置、契约、指标一起补齐
- 在 `task-scheduler` 内保留可拆分边界
- 阶段性进展同步记录到 `dev-progress`

开发后：

- 检查构建和诊断错误
- 检查命名、配置前缀、Redis Key 是否合规
- 影响架构语义时，同步更新文档

进度记录规范：

- 所有开发进度文档统一放在项目根目录 `dev-progress` 下
- 文件名格式固定为 `序号+时间+简单描述.md`
- 文件名样例：`1-20260417-架构搭建.md`
- 时间使用 `yyyyMMdd`
- 同一天多次记录时，按递增序号新增文件

## 12. 一页摘要

- 只实现 `A+` 架构，不实现 `LiteTopic`
- `P0` 启动项目只有 `task-gateway` 和 `task-scheduler`
- `callback`、`notify`、`compensator` 当前在 `task-scheduler` 内，但必须包级隔离
- 本期不做 `task-admin`
- 本期允许 `Nacos Config`，不允许 `Nacos Discovery`
- `MySQL` 负责事实，`Redis` 负责调度态，`RocketMQ` 负责一级缓冲
- 核心隔离维度固定为 `tenantId + taskType`
- 关键链路必须幂等
- 允许重复，不允许静默丢失
- 最终调度顺序只能由 `Dispatcher` 裁决
- Worker 发现依赖平台注册 + Redis 运行态视图

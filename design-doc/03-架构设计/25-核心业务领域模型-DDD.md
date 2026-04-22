# 核心业务领域模型（DDD）

## 1. 领域目标
对异步任务调度平台的核心对象、聚合边界与领域服务进行统一建模。

## 2. 核心限界上下文
- 任务接入上下文
- 任务调度上下文
- 执行与回调上下文
- 通知与补偿上下文
- Worker 治理上下文

## 3. 核心聚合与实体
### 3.1 Task 聚合
职责：
- 表示任务主实体
- 管理任务状态流转
- 关联租户、任务类型、回调配置、重试配置

### 3.2 TaskDispatch 聚合
职责：
- 表示一次调度派发行为
- 持有 `dispatchToken`
- 记录派发目标 Worker 与派发结果

### 3.3 TaskExecution 聚合
职责：
- 表示任务执行实例
- 记录开始时间、结束时间、异常信息、执行结果

### 3.4 TaskCallback 聚合
职责：
- 表示平台通知业务方的行为
- 管理回调状态机、重试次数、回调死信

### 3.5 WorkerInstance 聚合
职责：
- 表示 Worker 节点
- 管理能力标签、心跳状态、运行状态、优雅上下线

### 3.6 OutboxEvent 聚合
职责：
- 表示平台内部需要异步投递的事件
- 包括入队外盒与业务回调外盒

## 4. 领域服务
- TaskRoutingService：路由与选节点
- TaskRetryService：重试策略判断
- TimeoutDetectionService：超时扫描
- CallbackDeliveryService：业务回调投递
- WorkerHealthService：Worker 运行态收敛与健康判断

## 5. 值对象
- TaskId
- TenantId
- TaskType
- DispatchToken
- CallbackAddress
- WorkerStatus
- RetryPolicy
- ResourceSnapshot

## 6. 领域规则
- 任务终态不可逆
- Worker 回调不能覆盖新状态
- 通知状态与任务状态分离
- Worker 调度必须基于运行态视图

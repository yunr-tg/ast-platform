# AST Platform

AST 异步任务调度平台 - 企业级分布式任务调度解决方案。

## 目录

- [项目简介](#项目简介)
- [技术架构](#技术架构)
- [模块说明](#模块说明)
- [快速开始](#快速开始)
  - [环境要求](#环境要求)
  - [构建项目](#构建项目)
  - [运行任务网关](#运行任务网关)
- [API 使用指南](#api-使用指南)
  - [提交任务](#提交任务)
  - [取消任务](#取消任务)
  - [查询任务进度](#查询任务进度)
- [Worker SDK 使用指南](#worker-sdk-使用指南)
  - [引入依赖](#引入依赖)
  - [配置 Worker](#配置-worker)
  - [实现任务处理器](#实现任务处理器)
  - [进度上报](#进度上报)
- [配置说明](#配置说明)
- [架构设计](#架构设计)
- [下一步规划](#下一步规划)

## 项目简介

AST Platform 是一个高性能、高可用的异步任务调度平台，采用分层架构设计，支持任务提交、分发、执行、重试和回调等完整生命周期管理。

**核心特性：**
- 支持任务优先级调度
- 完整的任务状态管理
- 幂等性保证
- 分布式追踪支持
- 可观测性集成（Prometheus）
- Worker 优雅下线

## 技术架构

```
┌─────────────────────────────────────────────────────────────────┐
│                        API Gateway                             │
│  [POST /task/submit] [POST /task/cancel] [GET /task/progress]  │
└─────────────────────────┬───────────────────────────────────────┘
                          │
                          ▼
┌─────────────────────────────────────────────────────────────────┐
│                      Task Gateway                               │
│  [任务持久化] [Outbox模式] [RocketMQ消息发布]                    │
└─────────────────────────┬───────────────────────────────────────┘
                          │ RocketMQ
                          ▼
┌─────────────────────────────────────────────────────────────────┐
│                       Task Scheduler                           │
│  [Pump] → [Redis队列] → [Dispatcher] → [Worker]               │
│  [Retry] [Callback] [Notify] [Compensator]                     │
└─────────────────────────┬───────────────────────────────────────┘
                          │
                          ▼
┌─────────────────────────────────────────────────────────────────┐
│                        Workers                                  │
│  [任务执行] [进度上报] [结果回调]                                │
└─────────────────────────────────────────────────────────────────┘
```

## 模块说明

| 模块 | 说明 | 职责 |
|-----|------|-----|
| `ast-platform-common` | 公共工具模块 | 响应封装、异常处理、分布式追踪 |
| `ast-platform-domain` | 领域模型模块 | 任务状态机、Worker状态、业务规则 |
| `ast-platform-api-contract` | API契约模块 | Gateway和Worker的DTO定义 |
| `ast-platform-infra` | 基础设施模块 | Redis键约定、Lua脚本、配置 |
| `task-worker-sdk` | Worker SDK | Worker客户端、自动配置、任务处理框架 |
| `task-gateway` | 任务网关 | 任务提交入口、消息发布、幂等处理 |
| `task-scheduler` | 任务调度器 | 任务分发、重试、回调、补偿 |

## 快速开始

### 环境要求

- JDK 21+
- Maven 3.9+
- Redis 7.0+（可选，默认使用内存模式）
- RocketMQ 5.0+（可选，默认使用Mock模式）

### 构建项目

```bash
# 进入项目目录
cd ast-platform

# 编译并运行测试
mvn clean test

# 跳过测试快速构建
mvn clean package -DskipTests
```

### 运行任务网关

任务网关默认使用 H2 内存数据库，无需额外依赖即可启动：

```bash
cd task-gateway

# 方式1：Maven运行
mvn spring-boot:run

# 方式2：Jar运行
java -jar target/task-gateway-1.0.0.jar
```

启动成功后访问：
- API地址: `http://localhost:8080`
- 健康检查: `http://localhost:8080/actuator/health`
- Prometheus指标: `http://localhost:8080/actuator/prometheus`

## API 使用指南

### 提交任务

**接口地址**: `POST /task/submit`

**请求体**:
```json
{
  "tenantId": "tenant001",
  "taskType": "image-process",
  "bizKey": "order-12345",
  "requestId": "req-abc-123",
  "workerGroup": "image-workers",
  "tag": "vip",
  "payload": "{\"imageUrl\":\"https://example.com/image.jpg\",\"size\":\"1024x768\"}",
  "callbackUrl": "https://your-service.com/callback",
  "traceId": "trace-xyz-789",
  "priority": 1
}
```

**字段说明**:
| 字段 | 类型 | 必填 | 说明 |
|-----|------|-----|------|
| tenantId | String | 是 | 租户ID |
| taskType | String | 是 | 任务类型，用于路由到对应的Worker处理器 |
| bizKey | String | 是 | 业务主键，用于幂等判断 |
| requestId | String | 是 | 请求ID，唯一标识 |
| workerGroup | String | 是 | Worker分组名称 |
| tag | String | 否 | 任务标签，用于精细化路由 |
| payload | String | 否 | 任务负载数据（JSON格式） |
| callbackUrl | String | 否 | 任务完成后的回调地址 |
| traceId | String | 否 | 分布式追踪ID |
| priority | Integer | 否 | 优先级（1-10，数字越小优先级越高） |

**响应示例**:
```json
{
  "code": 200,
  "message": "success",
  "data": {
    "taskId": "task-uuid-123",
    "status": "PENDING",
    "idempotent": false,
    "outboxStatus": "PUBLISHED"
  }
}
```

### 取消任务

**接口地址**: `POST /task/cancel`

**请求体**:
```json
{
  "tenantId": "tenant001",
  "taskId": "task-uuid-123",
  "reason": "用户取消"
}
```

**响应示例**:
```json
{
  "code": 200,
  "message": "success",
  "data": null
}
```

### 查询任务进度

**接口地址**: `GET /api/v1/task/{taskId}/progress`

**路径参数**:
| 参数 | 类型 | 说明 |
|-----|------|------|
| taskId | String | 任务ID |

**响应示例**:
```json
{
  "taskId": "task-uuid-123",
  "percentage": 75,
  "message": "正在处理第3张图片",
  "payload": "{\"currentStep\": 3, \"totalSteps\": 4}",
  "timestamp": "2024-01-15T10:30:00Z"
}
```

## Worker SDK 使用指南

### 引入依赖

在你的 Spring Boot 项目中添加 Worker SDK 依赖：

```xml
<dependency>
    <groupId>com.ast.platform</groupId>
    <artifactId>task-worker-sdk</artifactId>
    <version>1.0.0</version>
</dependency>
```

### 配置 Worker

在 `application.yml` 中配置 Worker：

```yaml
ast:
  worker:
    worker-id: ${WORKER_ID:worker-001}
    worker-group: image-workers
    scheduler-endpoint: ${SCHEDULER_ENDPOINT:http://localhost:8081}
    heartbeat-interval-ms: 5000
    max-concurrent-tasks: 10
```

### 实现任务处理器

创建任务处理器类，实现 `TaskExecutionHandler` 接口：

```java
import com.ast.platform.workersdk.handler.TaskContext;
import com.ast.platform.workersdk.handler.TaskExecutionHandler;
import org.springframework.stereotype.Component;

@Component
public class ImageProcessHandler implements TaskExecutionHandler {

    @Override
    public String taskType() {
        return "image-process";
    }

    @Override
    public void handle(TaskContext context) {
        // 获取任务负载
        String payload = context.getPayload();
        
        // 解析业务数据
        ImageProcessRequest request = parsePayload(payload);
        
        // 执行任务逻辑
        processImage(request);
        
        // 任务完成
        context.complete("处理完成");
    }
    
    private void processImage(ImageProcessRequest request) {
        // 实际的图片处理逻辑
    }
}
```

### 进度上报

在长时间运行的任务中，可以使用 `ProgressReporter` 上报进度：

```java
@Override
public void handle(TaskContext context) {
    ProgressReporter reporter = context.getProgressReporter();
    
    // 上报进度（百分比, 消息, 附加数据）
    reporter.report(25, "开始处理", "{\"step\": \"init\"}");
    
    // 执行第一步
    doStep1();
    reporter.report(50, "第一步完成", "{\"step\": \"step1\"}");
    
    // 执行第二步
    doStep2();
    reporter.report(75, "第二步完成", "{\"step\": \"step2\"}");
    
    // 执行第三步
    doStep3();
    
    // 任务完成
    context.complete("全部完成");
}
```

## 配置说明

### 任务网关配置

```yaml
server:
  port: 8080

spring:
  application:
    name: task-gateway
  datasource:
    driver-class-name: org.h2.Driver
    url: jdbc:h2:mem:ast_gateway;MODE=MySQL

ast:
  gateway:
    storage-type: jdbc                    # 存储类型：jdbc / memory
    submit-topic: ast.task.submit         # RocketMQ Topic
    outbox-batch-size: 100                # Outbox批量处理大小
    immediate-publish-enabled: true       # 立即发布消息
    mock-send-success: true               # Mock模式（不真实发送MQ）
    outbox-republish-fixed-delay-ms: 3000 # Outbox重发间隔
    rocketmq:
      enabled: false                      # 是否启用RocketMQ
      endpoints: localhost:9876
      access-key: your-access-key
      secret-key: your-secret-key
```

### 调度器配置

```yaml
ast:
  scheduler:
    pump:
      enabled: true
      poll-interval-ms: 100
    dispatcher:
      enabled: true
      dispatch-strategy: least-busy       # 调度策略：round-robin / least-busy
    retry:
      max-attempts: 3
      delay-ms: 5000
```

## 架构设计

### 核心流程

1. **任务提交**: Client → Gateway → DB + Outbox → RocketMQ
2. **消息消费**: Pump → Redis Ready Queue
3. **任务分发**: Dispatcher → Worker
4. **任务执行**: Worker → Progress Report → Callback

### 状态机

```
PENDING → PROCESSING → COMPLETED
       ↘           ↘
        → RETRY     → FAILED → DLQ
```

### 调度策略

| 策略 | 说明 | 适用场景 |
|-----|------|---------|
| Round Robin | 轮询分发 | 负载均衡 |
| Least Busy | 最闲优先 | 资源利用率优化 |
| Priority | 优先级调度 | 紧急任务优先 |

## 下一步规划

- [ ] 添加 MySQL 任务事实表和 Outbox 持久化
- [ ] 完善 RocketMQ 生产者/消费者适配器
- [ ] 添加 Redis Ready/Processing/Retry 队列实现
- [ ] 实现 Worker 路由和运行时快照
- [ ] 完善幂等性状态持久化
- [ ] 添加死信队列（DLQ）治理
- [ ] 完善分布式追踪链路
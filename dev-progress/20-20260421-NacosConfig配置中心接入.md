# 开发进度记录 - 20260421

## 20. Nacos Config 配置中心接入

### 1. 依赖集成
- **Spring Cloud Alibaba**: 引入了 `spring-cloud-starter-alibaba-nacos-config`。
- **Spring Cloud Bootstrap**: 引入了 `spring-cloud-starter-bootstrap` 以支持传统的引导配置方式。
- **版本管理**: 在根 POM 中统一管理了 Spring Cloud (2023.0.3) 和 Spring Cloud Alibaba (2023.0.1.2) 的版本。

### 2. 配置引导
- **task-scheduler**: 增加了 `bootstrap.yml`，配置连接至 `${NACOS_SERVER_ADDR:127.0.0.1:8848}`。
- **task-gateway**: 增加了 `bootstrap.yml`，配置连接至 `${NACOS_SERVER_ADDR:127.0.0.1:8848}`。
- **动态刷新**: 
    - `SchedulerProperties` 采用了 `@ConfigurationProperties`，配合 Nacos 可实现秒级参数刷新。
    - 支持动态切换 `ast.scheduler.strategy`（调度算法）、`ast.scheduler.compensator.execution-timeout-ms`（超时阈值）等关键参数。

### 3. 共享配置
- 预留了 `ast-platform-common.yaml` 共享配置 DataID，方便统一管理数据库、Redis、RocketMQ 等公共连接信息。

## 下一步计划
- [ ] 实现任务执行进度的实时上报与查询 (Progress Tracking)
- [ ] 接入 Prometheus + Grafana 监控大屏，可视化各调度算法的性能指标
- [ ] 实现任务优先级的初步调度逻辑 (Priority Queue)

# 24-20260423-NacosConfig配置中心完整集成

## 背景
根据之前的分析，虽然Nacos Config依赖和基础配置已存在，但缺少完整的配置动态刷新机制、多环境支持和配置模板。本次对Nacos Config配置中心进行完整集成。

## 实现内容

### 1. bootstrap.yml 配置增强
- **task-gateway** 和 **task-scheduler** 的 bootstrap.yml 统一增强：
  - 添加 `spring.profiles.active` 支持多环境（默认dev）
  - 添加 `namespace` 支持Nacos命名空间隔离
  - 添加 `max-retry`、`config-retry-time`、`config-long-poll-timeout` 连接参数
  - 拆分共享配置为独立文件：
    - `ast-platform-common.yaml`：公共日志、监控配置
    - `ast-platform-datasource.yaml`：数据源配置
    - `ast-platform-redis.yaml`：Redis配置
    - `ast-platform-rocketmq.yaml`：RocketMQ配置
  - 添加 `extension-configs` 支持应用专属配置：
    - `{app-name}.yaml`：应用通用配置
    - `{app-name}-{profile}.yaml`：环境专属配置

### 2. @RefreshScope 动态刷新
- 为以下配置类添加 `@RefreshScope` 注解，支持运行时动态刷新：
  - `GatewayProperties` (`ast.gateway.*`)
  - `GatewayRocketMqProperties` (`ast.gateway.rocketmq.*`)
  - `SchedulerProperties` (`ast.scheduler.*`)
  - `CallbackProperties` (`ast.scheduler.callback.*`)
  - `NotifyProperties` (`ast.scheduler.notify.*`)
  - `CompensatorProperties` (`ast.scheduler.compensator.*`)
  - `SchedulerRocketMqProperties` (`ast.scheduler.rocketmq.*`)
- **注意**：`WorkerSdkProperties` 未添加 `@RefreshScope`，因为 Worker SDK 是轻量级依赖，不强制引入 Spring Cloud Context

### 3. 配置刷新监听器
- 新增 `NacosConfigRefreshListener`（task-gateway 和 task-scheduler 各一份）：
  - 监听 `RefreshScopeRefreshedEvent`：记录配置刷新日志
  - 监听 `RefreshEvent`：触发 `ContextRefresher.refresh()` 并记录结果

### 4. Nacos配置模板
- 创建 `deploy/nacos-config/` 目录，包含所有Nacos配置模板：
  - `ast-platform-common.yaml`：公共配置模板
  - `ast-platform-datasource.yaml`：数据源配置模板
  - `ast-platform-redis.yaml`：Redis配置模板
  - `ast-platform-rocketmq.yaml`：RocketMQ配置模板
  - `task-gateway.yaml`：Gateway应用配置模板
  - `task-scheduler.yaml`：Scheduler应用配置模板
  - `upload-config.sh`：一键上传脚本

### 5. 多环境支持
- 通过 `SPRING_PROFILES_ACTIVE` 环境变量切换环境（dev/staging/prod）
- 通过 `NACOS_NAMESPACE` 环境变量切换Nacos命名空间
- 环境专属配置：`{app-name}-{profile}.yaml`（如 `task-gateway-dev.yaml`）

## 技术要点

### 1. 配置优先级
```
环境变量 > 应用专属配置({app}-{profile}.yaml) > 应用通用配置({app}.yaml) > 共享配置 > application.yml > bootstrap.yml默认值
```

### 2. 动态刷新流程
```
Nacos Config变更 → 长轮询检测 → RefreshEvent → ContextRefresher.refresh() → @RefreshScope Bean重建 → 新配置生效
```

### 3. 安全考虑
- 敏感配置（数据库密码、RocketMQ密钥）通过环境变量注入
- Nacos Config中只存储非敏感配置项
- 支持Nacos命名空间隔离不同环境

## 验证结果
- 编译通过 ✅
- Nacos Config监听启动成功 ✅
- 所有共享配置data-id正确注册 ✅
- 应用专属配置data-id正确注册 ✅
- 多环境配置支持 ✅
- 配置动态刷新机制就绪 ✅

## 总结
本次实现了Nacos Config配置中心的完整集成，包括多环境支持、配置动态刷新、配置模板管理。现在可以通过Nacos Config统一管理所有运行时配置，支持运行时动态刷新，无需重启应用。
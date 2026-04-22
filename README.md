# AST Platform

P0 scaffold for the AST asynchronous task scheduling platform.

## Modules

- `ast-platform-common`
  - Shared response, exception and trace utilities.
- `ast-platform-domain`
  - Task state machine and worker status domain model.
- `ast-platform-api-contract`
  - Gateway and worker DTO contracts.
- `ast-platform-infra`
  - Redis key conventions and Lua script placeholders.
- `task-worker-sdk`
  - Worker control plane client and auto-configuration entry.
- `task-gateway`
  - Task submit entrypoint with `POST /tasks`.
- `task-scheduler`
  - Worker register, heartbeat, callback endpoints and isolated subsystem packages.

## Current Scope

- Follows the A+ design baseline:
  - `Gateway -> RocketMQ -> Pump -> Redis -> Dispatcher -> Worker`
- Keeps scheduler sub-systems isolated by package:
  - `pump`
  - `dispatcher`
  - `retry`
  - `callback`
  - `notify`
  - `compensator`
- Leaves storage, MQ, Redis, outbox, callback delivery and compensator internals as the next implementation step.

## Build

```bash
mvn test
```

## Next Step Suggestions

- Add MySQL task fact tables and outbox persistence.
- Add RocketMQ producer/consumer adapters.
- Add Redis ready/processing/retry repositories and Lua execution.
- Add worker routing and runtime snapshot repository.
- Add idempotent state persistence with version checks.

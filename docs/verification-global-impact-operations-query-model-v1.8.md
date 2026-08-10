# Global 影响运营态势查询模型 v1.8

## 1. 目标

为本地 UI 提供稳定、只读的全局影响任务运营态势契约，集中呈现停滞任务、SLO 严重度、恢复建议和任务处置入口。
前端不再聚合内部 `RuntimeState`，也不自行推断服务端停滞阈值。

## 2. 边界

- 仅查询 `PENDING`、`RUNNING` 且证据未过期的停滞任务；
- 停滞阈值和 Critical 阈值来自 Control Plane 的统一调度配置；
- 查询模型不提供处置命令，不改变 Worker 调度、租约和任务状态；
- 不新增数据库表或迁移，不读取原始 Provider 报文和 Secret；
- 恢复建议是服务端诊断结果，实际处置仍进入既有任务详情和受控命令流程。

## 3. API

```text
GET /control/v1/verification-drift-workbench/
    global-governance-policy-impact-operations-view?limit=100
```

`limit` 取值范围为 1～500，默认 100。响应包含：

- 服务端生成时间、停滞阈值和 Critical 阈值；
- stalled、critical、warning 数量；
- 按恢复建议聚合的数量；
- 任务身份、候选策略、优先级、进度、租约、停滞秒数、SLO 严重度和恢复建议；
- `limitReached`，用于提示当前视图可能被查询上限截断。

## 4. 查询与诊断规则

任务满足以下条件才进入运营态势：

```text
status in (PENDING, RUNNING)
and expiresAt > now
and lastProgressAt <= now - stallThreshold
```

按 `lastProgressAt ASC, jobId ASC` 返回，最久未推进的任务优先。严重度规则：

- `stalledSeconds >= criticalThresholdSeconds`：`CRITICAL`；
- 其余停滞任务：`WARNING`。

恢复建议规则：

- 尚未分派：`START_OR_ENABLE_WORKER`；
- 存在有效 Worker 租约：`WAIT_FOR_ACTIVE_WORKER`；
- 已分派但租约不存在或已到期：`REDISPATCH_AFTER_LEASE_EXPIRY`。

## 5. 工程实现

- `GlobalImpactJobQueryRepository.findStalledJobs`：稳定查询端口；
- `JdbcGlobalImpactJobQueryRepository`：单次 JOIN/聚合查询实现；
- `GlobalImpactOperationsQueryService`：SLO、恢复建议和响应聚合；
- `GlobalImpactOperationsQueryController`：独立只读 View API；
- 服务测试与控制器契约测试锁定查询规则和 HTTP 路由。

领域模块只声明查询端口，不依赖 Spring 或 JDBC；应用层不向 UI 暴露 JDBC 行模型。

## 6. 验收结果

- Java 21 Maven Reactor：300 tests，0 failures，0 errors；
- Control Plane 连接本地 MySQL `tpip_platform` 启动成功，40 个迁移校验通过；
- 新运营视图真实只读调用返回 stalled/critical/warning 均为 0；
- 既有 `/stalled` 查询返回空数组，两套查询口径一致；
- 验收后 Control Plane 已优雅停止，18082、18084、18100 均无监听进程；
- 未创建、更新或删除业务数据。

## 7. 后续演进

当停滞任务超过 500 条时，可增加游标分页和多维服务端过滤，但保持当前汇总字段和任务项语义兼容。进入共享环境后，
再按已登记的触发条件补充 OIDC/RBAC、操作人身份和数据范围控制。


# Verification 漂移治理提醒可观测性与工作台增强 v0.3

## 1. 目标与边界

v0.3 为治理提醒运营闭环增加三项只读能力：替代批次成员差异、批次到 Notification Outbox/渠道投递结果的
关联视图，以及 Workspace 级提醒积压与成功率指标。

本阶段不创建或修改批次，不批准、不提交、不重试投递，也不改变默认关闭的 DRAFT 自动预览配置。查询直接基于
V32 至 V34 已有账本和索引生成请求时刻快照，因此不新增数据库迁移或物化指标表。

## 2. API

```text
GET /control/v1/verification-drift-workbench/governance-reminder-batches/{batchId}/diff
GET /control/v1/verification-drift-workbench/governance-reminder-batches/{batchId}/delivery-status
GET /control/v1/verification-drift-workbench/governance-reminder-metrics?workspaceId={workspaceId}
```

三个接口均运行在只读事务中。

## 3. 替代批次差异

差异接口沿 `replacesBatchId/replacedByBatchId` 双向血缘确定旧批次和新批次，无论从血缘任一端查询，结果方向
始终是 `fromBatch -> toBatch`。血缘缺失或双向引用不一致时拒绝生成可能误导运营人员的差异。

成员按 `executionId` 稳定排序，并分为：

- `ADDED`：只存在于新批次；
- `REMOVED`：只存在于旧批次；
- `UNCHANGED`：提醒序号与评估 checksum 均相同；
- `MODIFIED`：同一执行账本的提醒序号或评估 checksum 发生变化。

单批次最多 100 个成员，因此 v0.3 返回完整差异，无需分页。响应只包含执行 ID、提醒序号和 checksum，不读取或
返回治理评估 payload。

## 4. 投递关联视图

未提交的批次返回 `submitted=false`；已提交批次通过不可变 `outboxId` 关联 Outbox 和渠道投递行。响应仅包含：

- Outbox 投递状态、路由状态及路由/送达时间；
- 渠道编码、渠道投递状态、尝试次数、送达或死信时间。

接口不返回 Endpoint、收件地址、模板渲染正文、Header、错误原文、归档内容或 Secret。若批次引用的 Outbox
不存在，视为数据一致性错误，而不是伪装成“尚未提交”。

## 5. Workspace 指标口径

指标是按 `workspaceId` 计算的请求时刻快照：

- 批次：总数及 `DRAFT/APPROVED/DISPATCHED/CANCELLED` 数量；
- 积压：当前已到期、预算未耗尽且没有当前提醒序号活动占用的 READY 执行数，以及最早到期时间；
- 占用：仍处于 DRAFT/APPROVED 批次且未释放的成员数；
- 路由：`UNROUTED/ROUTED/NO_MATCH/RENDER_FAILED` Outbox 数量；
- 渠道投递：`PENDING/CLAIMED/DELIVERED/DEAD_LETTER` 数量；
- 终态成功率：`DELIVERED / (DELIVERED + DEAD_LETTER) * 100`，保留两位小数；没有终态样本时为 `0.00`。

提交成功会释放该提醒轮次的成员占用；待办查询还会同时匹配当前 `reminderCount + 1`，避免历史已提交批次阻断
后续提醒周期。

## 6. 安全与一致性

- Repository 只投影安全字段，避免在应用层“取出后再脱敏”；
- 不跨 Workspace 汇总；未来接入 OIDC/RBAC 时三个接口应授予治理只读角色；
- 指标不是计费或审计事实，精确审计仍以批次、Outbox、Delivery 和 AuditEvent 原始账本为准；
- v0.3 不改变已发布资产、Bundle 或 Runtime 执行边界。

## 7. 验收标准

1. 双向替代血缘产生相同方向、稳定排序的差异；
2. 四种成员变化分类及汇总数量一致；
3. DRAFT 批次不读取 Outbox，已提交批次可关联渠道状态；
4. 响应不包含 Endpoint、正文、错误原文和 Secret；
5. Workspace 指标 SQL 可在 MySQL 8.4 执行，空 Workspace 返回零值；
6. 指标成功率只使用投递终态样本；
7. 所有接口只读且不触发批准、提交、投递或自动预览。


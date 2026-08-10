# Verification 漂移治理提醒批次与人工批准 v0.1

## 1. 目标与边界

提醒批次把已物化的治理执行账本转化为一份可审阅、可批准、可显式提交的通知意图，流程固定为：

```text
READY 且到期的执行账本
  -> 人工创建 DRAFT 预览批次
  -> 人工批准 APPROVED
  -> 人工显式提交 DISPATCHED
  -> Notification Outbox 异步路由与投递
```

v0.1 不提供定时扫描、自动批准或自动提交。批次提交不直接调用邮件、短信、企业微信等外部渠道，只创建统一
Notification Outbox 事件。V34 已在 v0.2 开放 `CANCELLED`、替代血缘和默认关闭的 DRAFT 预览生成器，详细
标准见 `docs/verification-drift-governance-reminder-operations-v0.2.md`。

## 2. 批次创建规则

创建命令接收 Workspace、环境编码和 1 至 100 个执行账本 ID。服务端重新读取执行账本，只允许同时满足：

1. 全部属于请求 Workspace；
2. 状态均为 `READY`；
3. `nextReminderAt` 已到期；
4. 提醒次数尚未达到最大预算；
5. 聚合键和负责人完全相同；
6. ID 均为不同的正整数。

客户端不能提交负责人、聚合键、提醒序号、评估 checksum 或通知正文。服务端按执行账本生成规范化 JSON，冻结
执行账本 ID、DriftReport ID、提醒序号和评估 checksum，并保存内容 SHA-256。唯一约束
`execution_id + reminder_no` 防止同一轮提醒被放入多个批次。

## 3. 批准与提交

状态机如下：

```text
DRAFT --approve(rowVersion)--> APPROVED --dispatch(rowVersion)--> DISPATCHED
```

批准和提交都要求 `X-Operator`，使用 `rowVersion` 防止覆盖并发操作，并记录操作者、时间和审计事件。本地单人
模式允许同一操作者完成两步；未来接入 OIDC/RBAC 后，应把创建、批准、提交拆为独立权限，并可要求职责分离。

提交阶段在数据库事务中执行：

1. 使用 `FOR UPDATE` 锁定批次；
2. 重新校验批次状态、版本、所有成员状态、提醒序号、checksum 和到期时间；
3. 创建 `TPIP_VERIFICATION_DRIFT_GOVERNANCE_REMINDER` Outbox；
4. 递增执行账本提醒次数，记录 `lastReminderAt/lastOutboxId` 并计算下一提醒时间；
5. 达到预算时把执行账本置为 `EXHAUSTED`；
6. 将批次置为 `DISPATCHED` 并写审计。

任何一步失败都会回滚整个事务，因此 Outbox 创建失败或并发校验失败不会消耗提醒预算，也不会留下已提交批次。
这里的预算表示“平台成功接受的提醒事件数”：Outbox 一旦创建成功，后续渠道路由、投递失败和重试不回退预算，
否则会破坏异步投递的确定性并可能重复通知。

## 4. API

```text
POST /control/v1/verification-drift-workbench/governance-reminder-batches
POST /control/v1/verification-drift-workbench/governance-reminder-batches/{batchId}:approve
POST /control/v1/verification-drift-workbench/governance-reminder-batches/{batchId}:dispatch
GET  /control/v1/verification-drift-workbench/governance-reminder-batches?workspaceId={workspaceId}
GET  /control/v1/verification-drift-workbench/governance-reminder-batches/{batchId}
```

创建示例：

```json
{"workspaceId":23,"environmentCode":"local","executionIds":[42,43]}
```

批准和提交命令：

```json
{"rowVersion":0}
```

列表与详情只返回安全运营字段、内容 checksum 和成员的执行账本 ID、提醒序号、评估 checksum，不返回冻结的
通知 payload、漂移报告文档、策略快照、端点、Header 或 Secret。

## 5. 数据库落地

V33：

- 为 `tpip_drift_governance_execution` 增加提醒间隔、最后提醒时间和最后 Outbox 引用；
- 新增 `tpip_drift_governance_reminder_batch`，保存状态、规范化 payload、checksum、乐观锁和操作证据；
- 新增 `tpip_drift_governance_reminder_batch_member`，冻结成员提醒序号和评估 checksum；
- 批次、成员、执行账本推进、Outbox 和审计由同一事务维护。

策略的 `reminderIntervalSeconds` 在执行账本物化时冻结，V33 对已有账本从策略快照回填，因此后续策略换版不会
改变已物化账本的提醒节奏。

## 6. 验收标准

1. 未到期、非 READY、预算耗尽、跨 Workspace、不同聚合键或不同负责人的账本不能组批；
2. DRAFT 未批准不能提交，过期 `rowVersion` 不能改变状态；
3. 同一执行账本的同一提醒序号最多属于一个批次；
4. 提交前事实变化必须拒绝且不创建 Outbox；
5. Outbox 创建失败不推进提醒次数，成功创建后批次和预算一起提交；
6. 重复读取已提交批次不重复创建 Outbox；
7. API 不暴露内部 payload 和敏感证据；
8. 系统启动后不会自动扫描、批准或提交批次。

## 7. 后续演进

批次关闭、替代和默认关闭的 DRAFT 预览生成器已经由 v0.2 落地。后续接入 OIDC/RBAC 后实施创建、批准、
提交权限与职责分离，并增加工作台 UI、批次差异预览和通知投递结果关联查询。

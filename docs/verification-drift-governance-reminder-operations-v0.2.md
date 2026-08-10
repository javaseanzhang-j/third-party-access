# Verification 漂移治理提醒批次运营闭环 v0.2

## 1. 目标

v0.2 在人工批准和显式提交基础上增加可审计的取消、替代和默认关闭的 DRAFT 预览生成器。它解决错误组批后
无法释放提醒轮次、批次重建丢失血缘，以及需要人工逐个选择所有到期账本的问题。

自动化边界保持不变：预览生成器只能创建 DRAFT，不能批准、提交、创建 Notification Outbox 或消耗提醒预算。

## 2. 取消

只有当前 `DRAFT` 或 `APPROVED` 批次可以取消：

```text
DRAFT/APPROVED --cancel(rowVersion, reason)--> CANCELLED
```

取消要求 1 至 500 字符原因和 `X-Operator`，记录 `cancelReason/cancelledBy/cancelledAt` 及审计事件。批次和成员
历史行不删除；成员的活动提醒轮次占用被释放，因此相同 `executionId + reminderNo` 可以进入新的批次。

`DISPATCHED` 不能取消，因为它已经创建 Outbox 并消耗提醒预算；如需纠正通知，应通过通知运营流程处理，而不是
篡改已提交的治理事实。

## 3. 替代

替代是一个原子命令：

1. 锁定并验证原批次状态和 `rowVersion`；
2. 验证替代成员仍为 READY、到期、预算可用；
3. 要求替代成员保持原聚合键、负责人和 Workspace；
4. 取消原批次并释放成员占用；
5. 创建新的 `MANUAL + DRAFT` 批次；
6. 写入 `old.replacedByBatchId` 与 `new.replacesBatchId` 双向血缘。

任一步失败则整体回滚，原批次继续有效，不产生孤立替代批次。

## 4. 自动预览生成器

生成器按到期时间读取没有活动批次占用的 `READY` 执行账本，再按
`workspaceId + aggregationKey + ownerCode` 稳定分组，每个批次最多 100 个成员。生成批次标记
`creationSource=AUTOMATION`，状态固定为 DRAFT。

默认配置：

```text
TPIP_DRIFT_REMINDER_PREVIEW_ENABLED=false
TPIP_DRIFT_REMINDER_PREVIEW_POLL_INTERVAL=5m
TPIP_DRIFT_REMINDER_PREVIEW_MAXIMUM_EXECUTIONS=500
TPIP_DRIFT_REMINDER_PREVIEW_MAXIMUM_BATCHES=10
TPIP_DRIFT_REMINDER_PREVIEW_ENVIRONMENT=local
TPIP_DRIFT_REMINDER_PREVIEW_ACTOR=drift-reminder-preview
```

单周期最多扫描 1000 个执行账本、尝试创建 100 个批次；数据库活动提醒唯一索引负责多实例竞争的最终仲裁。
并发冲突只跳过当前候选，不会触发批准、提交或预算推进。

## 5. API

```text
POST /control/v1/verification-drift-workbench/governance-reminder-batches/{batchId}:cancel
POST /control/v1/verification-drift-workbench/governance-reminder-batches/{batchId}:replace
```

取消请求：

```json
{"rowVersion":0,"reason":"recipient scope is incorrect"}
```

替代请求：

```json
{
  "rowVersion":0,
  "reason":"remove resolved execution",
  "environmentCode":"local",
  "executionIds":[42,43]
}
```

查询响应增加 `creationSource`、替代血缘和取消证据，不返回内部 payload。

## 6. V34 数据结构

- 批次增加创建来源、取消证据、前驱和后继批次引用；
- 成员增加 `reservationReleasedAt` 及活动占用生成列；
- 唯一约束从永久的 `executionId + reminderNo` 调整为仅约束未释放占用；
- 增加全局到期扫描索引和独立的成员执行账本外键索引；
- 取消、替代、成员占用和审计均由事务维护。

## 7. 验收标准

1. 只有 DRAFT/APPROVED 可取消，必须提供原因且受乐观锁保护；
2. 取消保留历史证据，同时允许相同提醒轮次重新组批；
3. 替代失败整体回滚，成功时双向血缘完整；
4. 替代不能跨 Workspace、聚合键或负责人；
5. 预览生成器默认关闭；
6. 自动批次只能是 `AUTOMATION + DRAFT`；
7. 自动化无批准、提交、Outbox 或预算推进能力；
8. 多实例并发依靠数据库活动占用唯一约束收敛。

## 8. 后续演进

批次成员差异、投递结果关联查询和治理指标已经由 v0.3 只读工作台实现，见
`docs/verification-drift-governance-reminder-observability-v0.3.md`。后续仍需在多用户环境接入 OIDC/RBAC，完成
创建/批准/提交职责分离。是否启用自动预览应由环境级变更流程决定，不能随应用版本默认开启。

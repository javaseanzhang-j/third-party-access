# Verification 漂移治理执行账本 v0.1

## 1. 目标与边界

治理执行账本位于“只读候选评估”和“通知 Outbox”之间，用于显式冻结一次可执行治理决策的上下文。它解决
策略后续暂停、换版或默认值变化后，无法复现当时为何把某份漂移报告纳入提醒的问题。

v0.1 只支持人工显式物化和只读查询，不提供定时扫描、领取、提醒计数递增或 Notification Outbox 创建能力。
物化账本不等于发送提醒。

## 2. 账本模型

每份 DriftReport 最多对应一条 `DriftGovernanceExecution`：

```text
DriftReport 1 --- 0..1 DriftGovernanceExecution
                           |
                           +--- frozen policy snapshot
                           +--- frozen evaluation snapshot
                           +--- aggregation key
                           +--- reminder budget
```

账本冻结：

- Workspace、DriftReport；
- 策略来源、Policy ID、PolicyVersion ID；
- 负责人和最大提醒次数；
- 确定性聚合键；
- 下一允许提醒时间；
- 规范化策略快照和评估快照；
- 两份快照组合内容的 SHA-256；
- 操作者和物化时间。

API 不返回内部快照文档，只返回 checksum 和运营字段。快照用于服务端审计、回放和未来 Outbox 物化。

## 3. 显式物化流程

```text
人工查看 governance-evaluations
  -> 选择 reminderCandidate=true 的 reportId
  -> POST governance-executions:materialize
  -> 服务端重新解析并评估当前事实
  -> 冻结策略与评估快照
  -> 创建 READY 账本并写审计
```

服务端必须重新评估，不能信任客户端提交的候选结论、负责人、策略版本或提醒预算。只有同时满足以下条件才能
创建：

1. DriftReport 属于请求 Workspace；
2. Review 为 `OPEN / ACKNOWLEDGED`；
3. 按当前有效策略已经超期；
4. 报告未被策略完全抑制。

同一 DriftReport 重复物化返回既有账本，不重新绑定新策略，也不增加提醒次数。数据库唯一约束负责多实例并发
下的最终幂等。

## 4. 聚合键

聚合键是以下规范化内容的 SHA-256：

- Workspace ID；
- 负责人；
- 策略来源和策略版本；
- 按 `aggregationWindow` 对齐的报告创建时间窗口；
- 排序后的未抑制 `checkCode + driftKind` 签名集合。

相同 Workspace、责任人、策略版本、窗口和漂移签名得到相同聚合键。v0.1 只计算和存储聚合键，不合并账本，
避免在提醒执行机制尚未建立前丢失报告级证据。

## 5. 状态和提醒预算

预留状态：

- `READY`：账本已经物化，尚未消耗完提醒预算；
- `EXHAUSTED`：未来执行器已经使用完最大提醒次数；
- `CLOSED`：Review 已解决或人工关闭执行。

v0.1 只创建 `READY`，且 `reminderCount=0`。`nextReminderAt` 初始为显式物化时刻，表示未来执行器最早可以
处理；当前没有执行器消费它。

## 6. API

```text
POST /control/v1/verification-drift-workbench/governance-executions:materialize
GET  /control/v1/verification-drift-workbench/governance-executions?workspaceId={workspaceId}
GET  /control/v1/verification-drift-workbench/governance-executions/{executionId}
```

物化请求：

```json
{"workspaceId":23,"reportId":42}
```

命令要求 `X-Operator`。客户端不能提交策略版本、聚合键、负责人、提醒次数、状态或快照。

## 7. 数据库与一致性

V32 新增 `tpip_drift_governance_execution`：

- `drift_report_id` 唯一，提供报告级幂等；
- 外键绑定 Workspace、Report、Policy 和 PolicyVersion；
- 内置默认要求 Policy 引用为空，Workspace/Global 策略要求引用完整；
- `JSON_VALID` 约束快照文档，使用 LONGTEXT 保持规范化原文字节顺序；
- checksum、预算、状态和来源均有数据库检查约束；
- Workspace 待执行扫描和聚合键建立组合索引。

物化账本与统一审计事件位于同一事务中。已落账快照不因策略生命周期变化而改写。

## 8. 验收标准

1. 非候选报告物化返回校验错误且不产生账本；
2. 候选报告只允许创建一条账本，重复命令幂等返回；
3. 策略和评估内容规范化后计算稳定 checksum；
4. 聚合键不受原始漂移项顺序影响；
5. 初始状态固定为 `READY`、提醒次数固定为 0；
6. API 不暴露内部快照、报告文档或 Secret；
7. v0.1 不创建 Outbox、不发送通知、不改变 Review。

## 9. 后续执行阶段

V33 已增加人工创建预览批次、人工批准和显式 Outbox 提交流程。只有 Outbox 与执行账本进度在同一事务中成功
提交后才消耗提醒预算；当前仍不启用自动扫描。详细标准见
`docs/verification-drift-governance-reminder-batch-v0.1.md`。

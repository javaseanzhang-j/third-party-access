# Verification 漂移治理只读评估 v0.1

## 1. 目标与边界

本阶段把已发布、已激活的漂移治理策略接入工作台，生成可解释的治理候选结果。评估只读取策略、DriftReport、
Review 和漂移项投影，不写入数据库，不发送通知，不改变 Review 状态。

评估必须限定一个 Workspace，因为不同 Workspace 可能命中不同策略版本。跨 Workspace 工作台原有
`reports / summary / groups` 契约保持不变，避免使用单一阈值错误覆盖多个 Workspace 的策略。

## 2. 评估流程

```text
workspaceId
  -> 解析 ACTIVE Workspace / Global / Built-in Default
  -> 查询该 Workspace 的 OPEN + ACKNOWLEDGED 报告
  -> 使用策略 overdueAfter 计算 dueAt / overdue
  -> 对每个漂移项计算类别抑制和检查项抑制
  -> 汇总 fullySuppressed
  -> 生成 reminderCandidate
```

`reminderCandidate` 仅表示“当前事实和策略允许进入后续提醒流程”，计算条件为：

```text
actionable && overdue && !fullySuppressed
```

它不是“已经创建通知”，也没有扣减 `maximumReminders`。提醒次数、间隔和聚合窗口在响应的策略快照中返回，
留给后续有状态执行器使用。

## 3. 抑制语义

每个漂移项分别返回：

- `kindSuppressed`：漂移类别命中 `suppressedDriftKinds`；
- `checkSuppressed`：检查编码命中 `suppressedCheckCodes`；
- `suppressed`：上述任一条件成立。

只有一份报告中的全部漂移项都被抑制，`fullySuppressed` 才为 `true`。部分抑制的报告继续作为候选，避免一个
被允许忽略的变化掩盖同一报告中的其他异常。若不可变报告与投影出现不应发生的不一致、查不到漂移项，评估
采用安全失败策略，不把报告判定为完全抑制。

## 4. API

```text
GET /control/v1/verification-drift-workbench/governance-evaluations
    ?workspaceId={workspaceId}&page=0&size=20
```

响应包含：

- 完整策略快照：解析来源、策略/版本 ID、超期/聚合/提醒参数、负责人和抑制清单；
- 报告证据：Review 状态、`rowVersion`、创建时间、截止时间、超期和候选结论；
- 漂移项证据：`itemNo + checkCode + driftKind` 及两类抑制命中结果；
- 分页信息和该 Workspace 的可行动报告总数。

接口不返回 DriftReport Document、Fixture、Baseline Snapshot、Provider 报文、决策原因或 Secret。

## 5. 一致性与安全

- 策略解析和工作台读取位于只读事务中；
- 响应固定返回命中的 `policyVersionId`，后续执行器必须将它写入执行快照；
- 评估不修改策略、Review、Baseline 或 Notification Outbox；
- 现有跨 Workspace API 继续使用显式查询阈值，不隐式混用多套策略；
- 当前本地单人模式不接入认证，未来 OIDC/RBAC 下该接口属于治理只读权限。

## 6. 验收标准

1. 策略暂停时使用内置默认，激活 Workspace 策略后返回该策略版本；
2. `dueAt` 由报告创建时间和策略超期时限确定性计算；
3. 类别或检查编码任一命中即可抑制单个漂移项；
4. 只有全部漂移项被抑制时报告才完全抑制；
5. 仅可行动、超期且未完全抑制的报告成为提醒候选；
6. 评估前后 Review `rowVersion` 和状态保持不变；
7. 不创建 Outbox、Delivery 或任何提醒状态。

## 7. 下一阶段

治理执行账本已经落地，支持人工显式冻结候选评估快照、策略版本、聚合键、提醒预算和下次允许时间，标准见
`docs/verification-drift-governance-execution-ledger-v0.1.md`。下一步是在默认关闭和人工批准前提下，从账本创建
幂等 Notification Outbox；执行器不得直接发送渠道消息。

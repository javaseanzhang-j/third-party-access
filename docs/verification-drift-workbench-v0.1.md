# Verification 漂移治理工作台 v0.1

## 1. 目标与边界

漂移治理工作台把分散在各个 Workspace 和 Baseline 下的 `DRIFTED` 报告汇总为可发现、可筛选、可排序的
治理待办，并提供状态总览与同类漂移聚合。它解决“必须先知道 reportId 才能治理”的问题。

v0.1 是只读查询层：不自动确认、接受或忽略漂移，不修改 Review，不发送升级通知。具体决策仍调用既有
`acknowledge / accept / dismiss` 命令，并继续接受 `rowVersion` 乐观并发控制。

治理策略资产已经落地，但 v0.1 工作台仍使用请求中的显式 `overdueAfterHours`，尚未隐式读取策略。策略
模型、三级解析和自动化边界见 `docs/drift-governance-policy-assets-v0.1.md`。

## 2. 工作台范围

查询范围 `scope`：

- `ACTIONABLE`：`OPEN + ACKNOWLEDGED`，报告列表和聚合默认值；
- `RESOLVED`：`ACCEPTED + DISMISSED`；
- `ALL`：全部治理状态，汇总接口默认值。

共同筛选条件：

- `workspaceId`：限定 Workspace；
- `driftKind`：`NEW_CHECK / MISSING_CHECK / STATUS_CHANGED / RESULT_CHANGED / EVIDENCE_CHANGED`；
- `checkCode`：精确匹配 VerificationCheck 编码；
- `assigneeCode`：精确匹配 v0.4 分派的负责人；
- `overdueOnly`：只返回可行动且超过时限的报告；
- `overdueAfterHours`：超期阈值，1 至 8760 小时，默认 72 小时。

超期从 DriftReport `createdAt` 开始计算，仅 `OPEN / ACKNOWLEDGED` 可以成为超期待办。已解决报告无论年龄
多长都不会标记为 overdue。时限是查询策略，不写入报告，因此改变阈值不会篡改历史事实。

## 3. API

```text
GET /control/v1/verification-drift-workbench/reports
GET /control/v1/verification-drift-workbench/summary
GET /control/v1/verification-drift-workbench/groups
GET /control/v1/verification-drift-workbench/governance-evaluations?workspaceId={workspaceId}
POST /control/v1/verification-drift-workbench/governance-executions:materialize
GET  /control/v1/verification-drift-workbench/governance-executions?workspaceId={workspaceId}
GET  /control/v1/verification-drift-workbench/governance-executions/{executionId}
POST /control/v1/verification-drift-workbench/governance-reminder-batches
POST /control/v1/verification-drift-workbench/governance-reminder-batches/{batchId}:approve
POST /control/v1/verification-drift-workbench/governance-reminder-batches/{batchId}:dispatch
POST /control/v1/verification-drift-workbench/governance-reminder-batches/{batchId}:cancel
POST /control/v1/verification-drift-workbench/governance-reminder-batches/{batchId}:replace
GET  /control/v1/verification-drift-workbench/governance-reminder-batches?workspaceId={workspaceId}
GET  /control/v1/verification-drift-workbench/governance-reminder-batches/{batchId}
GET  /control/v1/verification-drift-workbench/governance-reminder-batches/{batchId}/diff
GET  /control/v1/verification-drift-workbench/governance-reminder-batches/{batchId}/delivery-status
GET  /control/v1/verification-drift-workbench/governance-reminder-metrics?workspaceId={workspaceId}
POST /control/v1/verification-drift-workbench/governance-reviews:assign
POST /control/v1/verification-drift-workbench/governance-reviews:acknowledge
POST /control/v1/verification-drift-workbench/governance-reviews:dispose
GET  /control/v1/verification-drift-workbench/governance-operations/{commandKey}
```

`reports` 支持从 0 开始的分页，默认 20、最大 100，按 `OPEN → ACKNOWLEDGED → ACCEPTED → DISMISSED`、
报告时间和 ID 稳定排序。每条记录包含 Workspace、Baseline、Run、Review 状态、`rowVersion`、龄期、截止
时间、超期标记和规范化漂移签名，可直接进入既有决策流程。

`summary` 返回当前筛选范围的报告总数、可行动数、超期数、接受数、忽略数、受影响 Workspace 数和漂移项
数，并在 v0.4 增加已分派/未分派可行动数量。`groups` 按 `checkCode + driftKind` 聚合，返回报告数、可行动数、超期数、受影响 Workspace 数和最近
发生时间，默认最多 20 组、最大 100 组。

`governance-evaluations` 要求明确 Workspace，解析该 Workspace 的有效治理策略并返回策略快照、逐报告截止
时间、逐漂移项抑制证据和只读提醒候选结论。它不发送通知，详细标准见
`docs/verification-drift-governance-evaluation-v0.1.md`。

`governance-executions` 将人工选定的候选显式冻结为幂等执行账本，保存策略版本、聚合键和提醒预算，但不创建
通知。详细标准见 `docs/verification-drift-governance-execution-ledger-v0.1.md`。

`governance-reminder-batches` 将到期执行账本冻结为 DRAFT 预览，经人工批准和显式提交后，以同一事务创建
Notification Outbox 并推进提醒预算；当前没有自动扫描。详细标准见
`docs/verification-drift-governance-reminder-batch-v0.1.md`。

V34 支持取消、原子替代和默认关闭的 DRAFT 自动预览生成器；自动化不能批准或提交。详细标准见
`docs/verification-drift-governance-reminder-operations-v0.2.md`。

v0.3 增加替代成员差异、批次投递关联和 Workspace 指标三个只读视图，不新增迁移，也不改变自动化开关。
详细标准见 `docs/verification-drift-governance-reminder-observability-v0.3.md`。

v0.4 增加 Workspace 内负责人分派、批量确认/接受/忽略、Dry Run、幂等回放和完整命令证据；正式批量命令
采用全有或全无。详细标准见 `docs/verification-drift-workbench-operations-v0.4.md`。

## 4. 证据与检索投影

`tpip_verification_drift_report.report_document` 继续是不变的事实证据。直接用 `JSON_TABLE` 扫描所有历史
JSON 无法形成稳定高效的工作台，因此 V30 增加 `tpip_verification_drift_item` 检索投影：

```text
DriftReport 1 --- * DriftItem(itemNo, checkCode, driftKind)
```

- V30 使用 JSON_TABLE 从历史不可变报告一次性回填；
- 新报告在创建报告的同一事务中写入投影；
- 写入数量必须等于 `driftCount`，否则整个事务回滚；
- 投影只保存检查编码和漂移类别，不复制 baseline/current checksum 或任何报文；
- 投影可以从 Report Document 重建，不取代事实证据。

V30 同时增加 `(driftKind, checkCode, reportId)`、`(checkCode, driftKind, reportId)` 和 Review 工作台索引，
支持两种筛选顺序及状态扫描。

## 5. 安全与一致性

- API 不返回 Report Document、Baseline Snapshot、Fixture、Provider Response、URL、Header 或 Secret；
- 不返回确认说明或最终决策原因，避免跨 Workspace 汇总暴露人员输入；
- 查询运行在只读事务中，结果是请求时刻的已提交快照；
- 工作项携带最新 `rowVersion`，但调用决策命令时仍必须由写模型再次校验；
- 当前本地单人模式无需认证；未来接入 OIDC/RBAC 后，工作台汇总可授权给治理查看者，决策命令独立授权。

## 6. 验收标准

1. 默认列表只出现 `OPEN / ACKNOWLEDGED`；
2. Workspace、Scope、漂移类别、检查编码和超期组合筛选正确；
3. 分页总数和稳定排序正确；
4. 龄期、截止时间和超期判断可由同一阈值复算；
5. 同类聚合不重复计算同一报告；
6. 历史 JSON 报告可完整回填投影，新报告事务性写入投影；
7. API 不暴露原始证据和决策说明；
8. v0.1 不自动改变治理状态或发送通知。

## 7. 后续演进

1. 负责人分派、批量确认/处置和 Dry Run 已由 v0.4 落地；接入可信身份后再增加审批职责分离；
2. 批次成员差异和投递结果关联已经由提醒可观测性 v0.3 落地；
3. 增加 OIDC/RBAC 下的创建、批准和提交职责分离；
4. 运营 SLA、负责人负载和趋势数据 API 已由 v0.5 落地，SLA 策略绑定由 v0.6 落地；后续在其上建设基线树、
   聚合簇和趋势图交互界面，并在开始 UI 前执行提醒门禁。

# Verification 漂移治理策略切换影响分析 v0.7

## 1. 目标与边界

v0.7 在策略发布或激活前，对当前有效治理策略与指定候选版本进行只读差异分析。它回答阈值、负责人、提醒参数
和抑制规则变化后，有多少当前待办会新增/解除超期、进入/退出完全抑制，以及进入/退出提醒候选。

本阶段仍为后端发布门禁能力，不进入 UI；不发布、不激活、不暂停策略，不修改 Review/Baseline，也不生成提醒。

## 2. API

```text
GET /control/v1/verification-drift-workbench/governance-policy-impact
    ?workspaceId={workspaceId}
    &candidatePolicyId={policyId}
    &candidateVersionId={versionId}
    &page={0..}
    &size={1..100}
```

候选版本允许 `DRAFT` 或 `PUBLISHED`，以便在发布前评估。Workspace 策略只能分析其绑定的 Workspace；Global
策略可以分析任意真实 Workspace。当前策略继续使用 v0.6 的三级确定性解析。

## 3. 比较快照

响应同时冻结：

- 当前策略来源、策略/版本 ID、checksum、SLA、聚合窗口、提醒间隔、预算、负责人和抑制项数量；
- 候选策略作用域、版本号、生命周期、checksum 和相同参数；
- 参数的有符号差值，以及负责人、漂移类别抑制和检查编码抑制是否变化；
- 全部计算共用的 `snapshotAt`。

Built-in Default 没有策略和版本 ID，checksum 为 `null`。候选不可变版本的 checksum 用于复现分析输入。

## 4. 报告影响口径

只分析当前 `OPEN/ACKNOWLEDGED` 的 `DRIFTED` 报告：

- `overdue`：`report.createdAt + overdueAfter <= snapshotAt`；
- `fullySuppressed`：报告至少有一个规范化漂移项，且所有漂移项都命中类别或检查编码抑制；
- `reminderCandidate`：`overdue && !fullySuppressed`；
- 新增/移除使用当前布尔值与候选布尔值的集合差。

已解决报告不会重新进入未来待办，因此不参与策略切换运营影响。响应只分页返回超期、完全抑制或提醒候选结论
至少一项发生变化的报告，并携带当前/候选截止时间和 Review `rowVersion`。

## 5. 查询实现与扩展性

- JDBC 在 MySQL 中基于 V30 规范化漂移项投影执行集合判断，不解析 DriftReport JSON；
- 汇总、变化总数和分页明细均为数据库聚合，不把全部报告拉入 JVM；
- 空抑制集合显式编译为永不命中的条件，不生成非法 `IN ()`；
- 明细稳定按候选提醒候选、当前提醒候选、创建时间和 reportId 排序；
- v0.7 复用 V28/V30/V31 既有表和索引，不新增数据库迁移。

## 6. 安全与一致性

- API 不返回抑制规则明细、确认/处置说明、Report Document、Fixture、Provider 报文或 Secret；
- 只读分析不写 AuditEvent，策略发布与激活仍由既有命令审计；
- 候选策略和版本必须属于同一个策略资产；
- 当前解析版本缺失视为数据一致性故障，不静默回退；
- 接入 OIDC/RBAC 后，应把影响分析授予策略评审角色。

## 7. 验收标准

1. Built-in/Workspace/Global 当前策略来源都可作为比较基线；
2. DRAFT 和 PUBLISHED 候选均可分析；
3. Workspace 候选不能跨 Workspace；
4. SLA、提醒参数、负责人和抑制变化明确返回；
5. 新增/解除超期、抑制和提醒候选汇总可与分页明细对账；
6. 空和非空抑制集合 SQL 均可在 MySQL 8.4 执行；
7. 分析前后策略、Review、Baseline、命令和审计数量不变；
8. 不暴露原始证据和敏感配置。

## 8. 后续演进

1. 将影响分析设置为策略发布/激活命令的可选强制门禁证据；
2. 增加保存且可过期的评审快照，支持审批引用；
3. 进入策略对比与工作台 UI 前执行既定 UI 阶段提醒门禁；
4. 接入可信身份后增加评审人与发布人的职责分离。

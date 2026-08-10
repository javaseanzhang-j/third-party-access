# Verification Global 漂移治理策略聚合影响快照与覆盖门禁 v0.9

## 1. 目标与边界

v0.9 将 v0.8 的 Workspace 强门禁扩展到 Global 策略。Global 候选发布或激活前，必须对所有真实受影响
Workspace 完成同一时点的影响分析，形成不可变聚合快照；命令执行时重新计算覆盖集合，只有集合、当前基线、
候选版本和证据完整性全部一致才允许执行。

本阶段仍为后端治理能力，不进入 UI；不自动发布、激活或暂停策略，不引入身份审批流，也不把被 Workspace
专属策略遮蔽的租户错误纳入 Global 影响。

## 2. 受影响 Workspace 口径

以 `tpip_workspace` 中的真实 Workspace 为全集，逐个执行三级策略解析：

- 当前解析为 `WORKSPACE_POLICY`：Global 候选不会生效，从聚合覆盖中排除；
- 当前解析为 `GLOBAL_POLICY`：纳入覆盖，冻结当前 Global 策略/版本/checksum；
- 当前解析为 `BUILT_IN_DEFAULT`：纳入覆盖，当前策略身份均为空。

结果按 `workspaceId` 升序规范化并计算 `coverageChecksum`。新增受影响 Workspace、Workspace 专属策略启停、
当前 Global 策略变化都会改变 checksum，使旧快照失效。新增但已被 ACTIVE 专属策略遮蔽的 Workspace 不影响覆盖。

## 3. 标准流程

```text
选择 Global 候选版本
  -> 捕获受影响 Workspace 集合及当前基线
  -> 对每个 Workspace 生成 v0.8 不可变子快照
  -> 创建聚合头并绑定全部子快照
  -> 分页人工评审 Workspace 影响
  -> Global 发布/激活重新计算覆盖并校验全部证据
  -> 在同一事务中执行策略命令和一次性消费聚合快照
```

## 4. API

创建聚合快照：

```text
POST /control/v1/verification-drift-workbench/global-governance-policy-impact-snapshots
X-Operator: {actor}

{
  "candidatePolicyId": 1,
  "candidateVersionId": 1,
  "ttlSeconds": 3600
}
```

回查聚合头：

```text
GET /control/v1/verification-drift-workbench/global-governance-policy-impact-snapshots/{snapshotId}
```

分页回查 Workspace 子快照：

```text
GET /control/v1/verification-drift-workbench/global-governance-policy-impact-snapshots/{snapshotId}/workspace-snapshots
    ?page={0..}&size={1..100}
```

Global 发布或激活继续使用统一命令字段：

```json
{
  "rowVersion": 3,
  "impactSnapshotId": "3fffb975-60de-4b5d-8bd8-c02db9d5d788"
}
```

从 v0.9 起，Global 策略缺少聚合 `impactSnapshotId` 必须拒绝；单 Workspace 快照 ID 无法命中聚合仓储，也会拒绝。

## 5. 持久化模型

V37 新增：

- `tpip_global_drift_policy_impact_snapshot`：聚合头，保存候选身份、coverage checksum、Workspace 数量、
  聚合影响文档/checksum、有效期及发布/激活消费证据；
- `tpip_global_drift_policy_impact_snapshot_item`：按稳定顺序绑定 Workspace 与 v0.8 子快照，禁止子快照跨聚合复用。

聚合文档保存候选身份、覆盖 checksum、Workspace 数量以及每个 Workspace 的子快照 ID 和影响 checksum。
详细影响仍由子快照承载，查询接口分页返回，避免单次加载全部详情。

## 6. Global 命令门禁

命令进入策略仓储变更前必须校验：

1. 聚合快照存在、未过期且聚合文档 canonical SHA-256 一致；
2. 候选 policyId、versionId 和不可变 checksum 一致；
3. 对应发布或激活动作尚未消费；
4. 当前重新计算的 Workspace 数量与 coverage checksum 一致；
5. 明细数量、顺序和 Workspace 集合完整；
6. 每个子快照存在、未过期、文档 checksum 正确；
7. 每个子快照的候选身份和当前基线与实时覆盖条目一致。

策略命令与聚合快照消费处于同一事务。任何失败都不会修改策略生命周期，也不会消费聚合或子快照。
同一聚合快照可分别消费一次 publish 和一次 activation，两个动作互不替代。

## 7. 扩展性与安全

- 聚合头和明细分表，明细查询分页；
- 快照创建采用单个事务和统一 `snapshotAt`，保证覆盖与子证据同一观察点；
- 当前实现逐 Workspace 复用成熟的 v0.7 影响查询，优先保证口径一致；大规模租户阶段可替换为分片批处理；
- API 不返回抑制项明细、Report Document、Fixture、Provider 报文、处置说明或 Secret；
- 每个子快照和聚合快照分别产生创建审计，成功消费产生独立发布/激活审计。

## 8. 验收标准

1. 仅 GLOBAL 的 DRAFT/PUBLISHED 候选允许创建聚合快照；
2. ACTIVE Workspace 专属策略正确从覆盖集合排除；
3. Built-in 和当前 Global 基线均可冻结；
4. 聚合头与分页子快照可回查并对账；
5. 覆盖新增、减少、基线变化、明细缺失或顺序变化均拒绝；
6. 过期、候选不匹配、文档篡改和重复消费均拒绝；
7. Global 发布/激活缺少聚合快照时拒绝；
8. 验收过程不实际发布或激活策略。

## 9. 后续演进

1. 大规模 Workspace 分片计算、进度状态、失败续算和最终封板已由 v1.0 实现，见
   `docs/verification-global-drift-policy-impact-job-v1.0.md`；
2. 增加聚合风险分级、变化阈值和审批策略；
3. 接入可信身份后实现评审、发布、激活职责分离；
4. 进入影响对比或治理工作台 UI 前执行既定 UI 阶段提醒门禁。

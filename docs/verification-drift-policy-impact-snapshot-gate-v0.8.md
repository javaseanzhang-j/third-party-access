# Verification 漂移治理策略影响快照与发布门禁 v0.8

## 1. 目标与边界

v0.8 将 v0.7 的只读影响分析固化为不可变、可校验、可过期的评审快照，并把它接入 Workspace 治理策略的
发布与激活命令。这样，命令执行时校验的是评审人实际看过的候选版本和当前基线，而不是重新计算后无法追溯的
临时结果。

本阶段仍是后端治理能力，不进入 UI；不引入 OIDC/RBAC 或审批流，不自动发布、激活、暂停策略，也不改变
Global 策略现有人工发布路径。

## 2. 标准流程

```text
选择 Workspace 与候选版本
  -> 执行 v0.7 影响分析
  -> 创建不可变影响快照
  -> 人工评审快照摘要
  -> 发布命令校验并消费 publish 权限
  -> 激活命令重新校验并消费 activation 权限
  -> 写入策略命令审计与快照消费证据
```

发布与激活是两个独立动作，因此同一快照分别保存一次 `publish` 和一次 `activation` 消费证据。重复消费、过期、
候选不匹配、候选 checksum 漂移或当前有效基线变化都会拒绝命令。

## 3. API 契约

创建快照：

```text
POST /control/v1/verification-drift-workbench/governance-policy-impact-snapshots
X-Operator: {actor}

{
  "workspaceId": 23,
  "candidatePolicyId": 2,
  "candidateVersionId": 2,
  "ttlSeconds": 3600
}
```

`ttlSeconds` 范围为 300—86400。响应包含快照 ID、Workspace、候选及当前基线身份、两个 checksum、影响文档、
创建人与有效期，以及发布/激活消费证据。

回查快照：

```text
GET /control/v1/verification-drift-workbench/governance-policy-impact-snapshots/{snapshotId}
```

Workspace 策略发布或激活时传入快照：

```json
{
  "rowVersion": 5,
  "impactSnapshotId": "9502419e-c5be-44ee-9693-f811e5c010d9"
}
```

未提供 `impactSnapshotId` 的 Workspace 发布/激活命令必须拒绝。Global 策略暂时保持原有命令契约，等待后续按
全部受影响 Workspace 聚合生成覆盖快照，不能错误复用任意单 Workspace 快照。

## 4. 不可变快照

V36 新增 `tpip_drift_policy_impact_snapshot`，保存：

- 快照 ID、Workspace 和候选策略/版本/checksum；
- 创建时解析到的当前策略/版本/checksum；Built-in Default 使用空 ID 与空 checksum；
- 规范化影响文档及其 SHA-256 checksum；读取 MySQL JSON 后重新 canonicalize 再校验，避免字段重排干扰；
- 创建人、创建时间、过期时间；
- 发布与激活各自的一次性消费人和消费时间。

影响文档仅保存 v0.7 的安全策略摘要、参数差异和汇总，不保存 Report Document、Fixture、Provider 报文、
处置说明、抑制项明细或 Secret。已创建快照不原地更新；消费字段仅能从未消费推进为已消费。

## 5. 命令门禁

Workspace 发布/激活进入领域仓储变更前，按顺序校验：

1. 快照存在且未过期；
2. 影响文档重新规范化后的 SHA-256 与快照 checksum 一致；
3. 快照 Workspace 与策略 Workspace 一致；
4. 候选策略、版本和不可变 checksum 一致；
5. 快照记录的当前有效策略基线与命令执行时的三级解析结果一致；
6. 对应动作尚未消费该快照。

策略命令与快照消费在同一事务内提交。任一步失败都不会推进策略生命周期，也不会提前消费快照。成功消费写入
独立 AuditEvent，保留 actor、快照、候选和动作证据。

## 6. 一致性与并发

- 策略自身继续使用 `rowVersion` 乐观锁；
- 候选身份同时绑定 policyId、versionId 和 checksum；
- 当前基线同时绑定来源、policyId、versionId 和 checksum；
- 数据库条件更新保证每种动作最多消费一次；
- 快照生成和命令执行分离，基线在期间改变时强制重新分析和评审。

## 7. 验收标准

1. DRAFT/PUBLISHED Workspace 候选可创建有时效的不可变快照；
2. 快照可按 ID 回查，影响 checksum 可复核；
3. Workspace 发布/激活缺少快照时拒绝；
4. 过期、候选不匹配、checksum 不匹配、基线变化和重复消费均拒绝；
5. 发布消费不阻止同一快照首次激活消费，反之亦然；
6. 门禁失败不改变策略、快照消费状态或业务治理事实；
7. Global 策略不接受单 Workspace 快照作为全局影响证明；
8. API 和审计不暴露原始证据及敏感配置。

## 8. 后续演进

1. Global 聚合覆盖快照与完整性门禁已由 v0.9 实现，见
   `docs/verification-global-drift-policy-impact-snapshot-gate-v0.9.md`；
2. 接入可信身份后增加评审、发布、激活职责分离和 RBAC；
3. 增加快照保留、归档和到期清理策略；
4. 进入策略对比或工作台 UI 前，必须先执行既定 UI 阶段提醒门禁。

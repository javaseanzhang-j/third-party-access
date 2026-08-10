# Verification 漂移治理策略资产 v0.1

## 1. 目标与边界

本标准把 Verification 漂移治理中的超期、归属、聚合、提醒预算和抑制规则从调用参数提升为可版本化、
可发布、可审计的治理资产，并支持 Workspace 级差异化配置。

v0.1 只负责策略配置、生命周期和确定性解析，不执行自动提醒、批量处置或治理状态变更。现有漂移工作台仍
以显式查询参数计算超期；后续自动化必须只消费已解析的发布版本，不得直接读取草稿配置。

## 2. 资产模型

```text
DriftGovernancePolicy 1 --- n DriftGovernancePolicyVersion
         |
         +--- GLOBAL
         \--- WORKSPACE(workspaceId)
```

- `DriftGovernancePolicy`：稳定身份，保存编码、名称、作用域、当前发布版本、运行状态和 `rowVersion`；
- `DriftGovernancePolicyVersion`：不可变配置快照，内容规范化后计算 SHA-256；
- `GLOBAL`：平台默认治理策略，不绑定 Workspace；
- `WORKSPACE`：仅对指定 Workspace 生效，创建时必须验证 Workspace 存在。

已发布版本不可原地修改。配置变化必须创建新版本、发布并重新激活，历史版本继续用于审计和复现。

## 3. 配置字段

| 字段 | 约束 | 语义 |
| --- | --- | --- |
| `overdueAfterSeconds` | 1 小时至 365 天 | 漂移报告进入超期状态的时限 |
| `aggregationWindowSeconds` | 1 小时至 30 天 | 后续自动化聚合同类漂移的时间窗口 |
| `reminderIntervalSeconds` | 1 小时至 30 天 | 后续重复提醒之间的最小间隔 |
| `maximumReminders` | 1 至 100 | 单个治理对象允许产生的提醒上限 |
| `ownerCode` | 1 至 100 字符 | 默认治理责任人或责任组编码 |
| `suppressedDriftKinds` | 最多 5 个、不可重复 | 按漂移类别抑制 |
| `suppressedCheckCodes` | 最多 100 个、不可重复 | 按 VerificationCheck 编码抑制 |

抑制列表在计算 checksum 前排序，因此相同语义配置具有相同内容身份。只读评估器的标准匹配语义为：漂移项的
类别命中 `suppressedDriftKinds`，或检查编码命中 `suppressedCheckCodes`，该项即被抑制；只有报告中的全部
漂移项均被抑制时，报告才从提醒候选中排除。该语义已经接入工作台只读评估，但尚未接入通知自动化。

## 4. 生命周期与配置流程

```text
创建 Policy(DRAFT)
  -> 创建 Version(DRAFT)
  -> 发布 Version(PUBLISHED)，Policy 进入 PAUSED
  -> 激活 Policy(ACTIVE)
  -> 暂停 Policy(PAUSED)
  -> 创建下一不可变 Version
```

- 没有发布版本的策略不能激活；
- 发布版本不会隐式启用策略，必须单独执行激活命令；
- 发布新版本时策略保持 `PAUSED`，防止配置变更未经确认直接影响后续自动化；
- 激活、暂停和发布均校验 `rowVersion`，并写入统一审计事件；
- 数据库唯一约束保证最多一个活动全局策略，以及每个 Workspace 最多一个活动策略。

## 5. 解析优先级

调用方必须按 Workspace 解析有效配置，不自行拼接或合并多个版本：

```text
ACTIVE Workspace Policy
        > ACTIVE Global Policy
        > Built-in Default
```

内置默认值用于系统初始可用和配置失效保护：

- 超期：72 小时；
- 聚合窗口：24 小时；
- 提醒间隔：24 小时；
- 最大提醒次数：3；
- 负责人：`unassigned`；
- 无抑制规则。

解析结果明确返回 `WORKSPACE_POLICY / GLOBAL_POLICY / BUILT_IN_DEFAULT` 和命中的策略、版本 ID，便于后续
执行快照和审计。解析的是完整版本，不做字段级继承，避免不同版本拼接后无法复现。

## 6. API

```text
POST /control/v1/drift-governance-policies
GET  /control/v1/drift-governance-policies
GET  /control/v1/drift-governance-policies/{policyId}
POST /control/v1/drift-governance-policies/{policyId}/versions
GET  /control/v1/drift-governance-policies/{policyId}/versions
POST /control/v1/drift-governance-policies/{policyId}/versions/{versionId}:publish
POST /control/v1/drift-governance-policies/{policyId}:activate
POST /control/v1/drift-governance-policies/{policyId}:pause
GET  /control/v1/drift-governance-policies:resolve?workspaceId={workspaceId}
```

命令接口要求 `X-Operator`。查询和解析接口不返回 DriftReport 文档、Fixture、Provider 报文或 Secret。

## 7. 数据库与一致性

V31 新增：

- `tpip_drift_governance_policy`：稳定身份、作用域、当前版本、状态和乐观锁；
- `tpip_drift_governance_policy_version`：不可变配置、规范化抑制列表、checksum 和发布时间；
- 生成列唯一索引：约束活动全局策略和活动 Workspace 策略；
- Workspace、当前版本和版本所属策略外键：防止悬空引用。

应用服务负责发布事务边界，仓储只实现持久化端口，避免基础设施代理语义泄漏到领域模型。发布版本、更新当前
版本指针和审计必须在同一事务提交。

## 8. 验收标准

1. Workspace 作用域必须引用真实 Workspace，GLOBAL 不得绑定 Workspace；
2. 版本字段范围、重复抑制项和 checksum 规则均在领域边界校验；
3. 发布版本后策略为 `PAUSED`，显式激活后才能参与解析；
4. Workspace、全局和内置默认三级回退顺序确定且可复现；
5. 同一层级不能同时存在两个活动策略；
6. 并发发布、激活和暂停通过 `rowVersion` 拒绝陈旧命令；
7. v0.1 不发送通知、不自动改变 Review 状态，也不覆盖不可变漂移证据。

## 9. 后续落地顺序

1. 建设有状态治理执行账本，固化策略版本、聚合键、提醒次数和下次允许时间；
2. 增加人工预览、按策略版本回放和指标；
3. 通过幂等 Notification Outbox 产生提醒事件，全局开关保持默认关闭；
4. 接入 OIDC/RBAC 后，对策略发布和激活实施职责分离。

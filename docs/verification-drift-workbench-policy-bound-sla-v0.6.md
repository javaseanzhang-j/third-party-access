# Verification 漂移治理工作台策略绑定 SLA v0.6

## 1. 目标与边界

v0.6 将 v0.5 运营指标的 SLA 从调用方默认值升级为受治理策略口径。指标服务在每次请求中通过统一解析器获取
当前有效的不可变策略版本，遵循 `Workspace Policy > Global Policy > Built-in Default`，并在响应中返回策略
来源和版本证据。

本阶段仍是后端治理建设，不进入 UI。它不创建或修改策略、不改变 Review/Baseline、不写指标快照，也不启动
告警和自动处置。

## 2. API 兼容与调用方式

```text
GET /control/v1/verification-drift-workbench/governance-metrics
    ?workspaceId={workspaceId}
    &windowDays={1..90，默认30}
    [&slaHours={1..8760，仅分析覆盖}]
```

- 不传 `slaHours`：`slaPolicy.mode=POLICY`，使用解析后策略的 `overdueAfter`；
- 传 `slaHours`：`slaPolicy.mode=EXPLICIT_ANALYSIS_OVERRIDE`，只覆盖本次只读计算；
- 显式覆盖不更新策略、不写 AuditEvent，也不能被自动化和正式报表当作策略事实；
- `slaHours` 顶层字段为 v0.5 小时级兼容字段，`slaSeconds` 和策略证据中的秒数是 v0.6 权威口径。

## 3. 策略证据

响应新增：

```json
{
  "slaHours": 72,
  "slaSeconds": 259200,
  "slaPolicy": {
    "mode": "POLICY",
    "source": "WORKSPACE_POLICY",
    "policyId": 12,
    "policyVersionId": 37,
    "configuredSlaSeconds": 259200,
    "effectiveSlaSeconds": 259200,
    "ownerCode": "integration-governance"
  }
}
```

内置默认返回 `source=BUILT_IN_DEFAULT` 且策略、版本 ID 为 `null`。全局策略返回 `GLOBAL_POLICY`。显式覆盖时
仍返回解析到的策略和 `configuredSlaSeconds`，并通过不同的 `effectiveSlaSeconds` 表达本次假设，避免丢失对照基线。

## 4. 一致性规则

1. 策略解析先验证 Workspace 存在，不存在的 Workspace 不返回伪零指标；
2. 一个指标响应只解析一次策略，并以同一个 `snapshotAt` 完成全部查询；
3. SLA 截止、当前超期和历史处置达标使用同一个精确 Duration；
4. 策略版本不可变，因此 `policyId + policyVersionId + configuredSlaSeconds` 可复现当次口径；
5. Built-in Default 仍为 72 小时，保证未配置策略时系统可用；
6. 解析到范围外策略属于数据一致性故障，不静默回退或截断。

## 5. 安全与审计

- 响应仅增加策略标识、负责人编码和阈值，不返回抑制规则、人员说明或敏感配置；
- 指标 GET 不写审计，正式策略发布/激活审计仍由策略资产服务负责；
- 显式分析覆盖在响应中自描述，不伪装为正式策略结果；
- 接入 OIDC/RBAC 后，应限制分析覆盖能力，但本地单用户阶段不模拟权限。

## 6. 数据库影响

v0.6 不新增迁移。它复用 V31 策略资产和 V28/V30/V35 漂移治理事实及索引，不复制策略快照到指标表。

## 7. 验收标准

1. 不传 `slaHours` 时使用当前解析策略并返回 `POLICY`；
2. Workspace、Global、Built-in Default 三种来源都能明确表达；
3. 显式覆盖只改变本次有效阈值，并返回 `EXPLICIT_ANALYSIS_OVERRIDE`；
4. 策略配置和生效阈值以秒精确表达，不发生小时截断；
5. 不存在的 Workspace 被拒绝；
6. 请求前后策略、Review、Baseline、命令和 AuditEvent 不变；
7. v0.5 指标、趋势和零分母语义保持兼容。

## 8. 后续演进

1. 为策略切换前后提供并列对比和影响报告；
2. 将正式策略口径暴露为 Prometheus 指标并建设长期 SLO；
3. 进入交互工作台 UI 前执行既定 UI 阶段提醒门禁；
4. 接入可信身份后限制显式分析覆盖及跨 Workspace 查询。

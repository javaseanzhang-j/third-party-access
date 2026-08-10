# RegressionPolicy 受控基线换版 v0.1

## 1. 目标与原则

当 Verification 漂移被确认并接受后，系统可以创建不可变后继基线，但不会自动改变无人值守回归标准。
RegressionPolicy 必须通过新的不可变版本显式引用后继基线，再独立发布和激活，使“接受事实”与“启用标准”
成为两个可审计动作。

策略根上的 `baselineId` 保留为初始基线和兼容字段；真正参与调度执行的是已发布
`RegressionPolicyVersion.baselineId`。历史版本始终保留当时执行的基线，不会因后续换版而被重写。

## 2. 受控转换规则

创建策略首个版本时只能使用策略初始基线。后续版本未传 `baselineId` 时继承当前版本基线，适合只调整
执行间隔、失败退避和连续失败阈值；显式换版必须同时满足：

1. 目标基线是当前版本基线的直接后继，禁止跳级、旁路和回退；
2. 目标基线带有 `acceptedDriftReportId`，证明它来自已接受漂移；
3. 前后基线属于同一 Workspace；
4. 前后基线引用同一 FixtureSuiteVersion。

创建草稿和发布版本时都会校验转换。这样，即使并发创建了多个草稿，先发布的版本改变当前基线后，其他
草稿也不能绕过最新血缘重新发布。

## 3. 配置与运行流程

```text
DRIFTED -> ACKNOWLEDGED -> ACCEPTED -> 创建不可变后继基线
                                      |
                                      v
暂停 RegressionPolicy -> 创建引用后继基线的 DRAFT Version
                       -> 发布 Version（再次校验血缘）
                       -> 激活 Policy（指定首次执行时间）
                       -> Scheduler 使用 Version.baselineId 执行
```

换版 API 沿用版本创建接口，`baselineId` 为可选字段：

```http
POST /control/v1/regression-policies/{policyId}/versions
X-Operator: owner
Content-Type: application/json

{
  "baselineId": 4,
  "intervalSeconds": 7200,
  "failureBackoffSeconds": 600,
  "maximumConsecutiveFailures": 5
}
```

发布、激活和暂停仍使用既有 API 与 `rowVersion` 乐观并发控制。策略必须处于暂停状态才能发布新版本；发布
不会自动激活。调度候选、Verification 执行、漂移通知 Outbox 中的基线身份均取自当前策略版本。

## 4. 数据模型与迁移

V29 为 `tpip_regression_policy_version` 增加非空 `baseline_id` 和 VerificationBaseline 外键，并用策略初始
基线回填历史版本。因此升级后历史策略行为不变，新版本开始具备独立、不可变的基线身份。

血缘和漂移接受条件属于跨聚合业务约束，由应用服务在创建和发布两个边界校验；数据库外键保证被引用基线
存在。发布后的策略版本继续保持不可变。

## 5. 安全与治理边界

- 接受漂移不自动创建、发布或激活策略版本；
- 换版不绕过 Verification Engine 的远程调用、安全和幂等门禁；
- API 要求 `X-Operator`，生命周期动作进入审计事件；
- 当前本地单人使用场景采用显式操作与血缘门禁；接入 OIDC/RBAC 后，可将漂移接受、版本发布和策略激活
  分配给不同角色，并增加双人审批；
- 若需要更换 FixtureSuiteVersion，应先建立新的 FULL 验证基线和独立策略，不通过本流程跨测试资产换版。

## 6. 验收标准

1. 不传基线可创建同基线的调度参数版本；
2. 已接受的直接后继可以创建、发布并激活；
3. 无关基线、未接受基线、跨 Workspace、跨 FixtureSuiteVersion、跳级或回退均被拒绝；
4. 旧版本仍返回旧基线，新版本返回后继基线；
5. Scheduler 与通知使用版本基线，不使用策略初始基线；
6. 发布时转换失效会被再次拒绝；
7. 升级到 V29 后历史版本完成回填且原有策略行为不变。

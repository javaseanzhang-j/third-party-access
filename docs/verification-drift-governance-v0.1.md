# Verification 漂移处置与基线演进 v0.1

## 1. 目标

将 `DRIFTED` 从技术检测结果升级为可审计的治理流程。系统允许负责人先确认漂移，再选择忽略或接受；接受
不会覆盖旧基线，而是从已验证的回归运行创建不可变后继基线，保留完整血缘。

## 2. 状态机

```text
DRIFTED report -> OPEN -> ACKNOWLEDGED -> ACCEPTED
                                      \-> DISMISSED
```

- `OPEN`：系统检测到漂移，等待调查；
- `ACKNOWLEDGED`：负责人确认已看到并记录调查说明；
- `ACCEPTED`：确认属于预期变化，生成后继基线；
- `DISMISSED`：判定为误报、临时噪声或无需形成新基线的变化。

状态只能单向流转，命令携带 `rowVersion`，并发重复确认或决策会被拒绝。只有 `DRIFTED` 报告存在治理记录，
`NO_DRIFT` 不制造待办噪声。

## 3. 接受漂移的安全条件

接受前必须满足：

1. 报告状态为 `DRIFTED`；
2. 治理记录已经 `ACKNOWLEDGED`；
3. 报告引用的运行是 `PASSED REGRESSION`；
4. 运行由服务端执行；
5. Workspace 和 FixtureSuiteVersion 与前驱基线一致；
6. 当前运行仍有完整 VerificationCheck 证据。

新基线重新使用当前 Profile 捕获检查指纹，并保存 `predecessorBaselineId` 和 `acceptedDriftReportId`。旧基线、
旧报告和旧运行保持不可变。创建后继基线与将治理记录置为 `ACCEPTED` 位于同一事务中。

## 4. 与 RegressionPolicy 的边界

接受漂移不会自动切换现有 RegressionPolicy。自动切换会把“承认新事实”和“改变无人值守执行标准”合并成
一个高风险操作。现已提供受控策略换版：创建引用已接受直接后继基线的不可变 PolicyVersion，并独立发布、
激活；详细规则见 `docs/regression-policy-baseline-transition-v0.1.md`。

## 5. API

```text
GET  /control/v1/verification-drift-reports/{reportId}/review
POST /control/v1/verification-drift-reports/{reportId}:acknowledge
POST /control/v1/verification-drift-reports/{reportId}:accept
POST /control/v1/verification-drift-reports/{reportId}:dismiss
```

命令体：

```json
{"rowVersion":0,"reason":"调查或决策说明"}
```

确认、接受和忽略均要求 `X-Operator`，说明限制为 1 至 1000 字符，并写入统一审计事件。

## 6. 数据库

V28：

- 为 `tpip_verification_baseline` 增加前驱基线和被接受漂移报告外键；
- 新增 `tpip_verification_drift_review`，保存状态、乐观锁、确认人与最终决策；
- 为历史 `DRIFTED` 报告回填 `OPEN` 治理记录；
- 唯一约束保证一个漂移报告最多生成一个后继基线。

## 7. 后续演进

1. 对接 OIDC/RBAC 后实施确认人与批准人职责分离；
2. 增加批量抑制、同类漂移聚合和过期升级；
3. 对 RegressionPolicy 基线换版增加职责分离与双人审批；
4. 在现有只读血缘、趋势和影响 API 之上增加交互式可视化。

只读基线树、漂移决策聚合和 RegressionPolicy 影响范围已经落地，标准见
`docs/verification-baseline-lineage-impact-analysis-v0.1.md`。

跨 Workspace 待办发现、超期判断和同类漂移聚合已经落地，标准见
`docs/verification-drift-workbench-v0.1.md`。

Workspace/全局两级治理策略、不可变版本、超期/聚合/提醒预算和抑制配置已经资产化，标准见
`docs/drift-governance-policy-assets-v0.1.md`。当前只提供配置和解析，不执行自动通知或批量决策。

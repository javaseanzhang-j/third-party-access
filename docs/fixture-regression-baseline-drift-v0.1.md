# FixtureSuite 回归基线与漂移检测 v0.1

## 1. 目标

将一次已经通过的 Workspace 服务端验证固化为不可变、脱敏的回归基线。后续使用同一个 Workspace、
BindingVersion 和 FixtureSuiteVersion 再次运行 Verification Engine，并把结果与基线指纹比较，识别第三方
报文、映射结果、Policy 行为、Bundle 预览或远程调用行为的变化。

基线能力本身提供手动触发 API。可选的 RegressionPolicy v0.1 在全局默认关闭的前提下增加固定间隔调度、
数据库租约、失败退避、自动暂停和漂移告警，详见 `docs/regression-policy-scheduler-v0.1.md`。

## 2. 资产模型

```text
PASSED FULL VerificationRun
  +-- VerificationCheck Evidence
          |
          v  capture fingerprints
VerificationBaseline (immutable)
          |
          +-- run REGRESSION VerificationRun
                    |
                    v compare fingerprints
          VerificationDriftReport (immutable)
```

- `VerificationBaseline`：引用 Workspace、FixtureSuiteVersion 和来源 VerificationRun，保存脱敏检查指纹快照
  及 SHA-256；来源必须是 `PASSED + FULL + server-executed`。
- `REGRESSION VerificationRun`：复用原 Verification Engine，不改变 Workspace 生命周期。
- `VerificationDriftReport`：记录 `NO_DRIFT / DRIFTED`、比较检查数、漂移项数和脱敏报告。

## 3. 基线内容

基线不会复制 Fixture 输入、映射输出、HTTP Header、Provider 原始响应或 Secret。每个 Check 只保存：

```json
{
  "checkCode": "FIXTURE.remote.success",
  "status": "PASSED",
  "resultChecksum": "sha256...",
  "evidenceChecksum": "sha256..."
}
```

整个快照使用 `tpip-verification-baseline/v0.2` Profile，字段排序后再次计算 `baselineChecksum`。Profile 是
指纹算法兼容边界；v0.2 明确排除临时候选 Bundle 身份，旧 Profile 不会被新比较器静默解释。

## 4. 漂移类型

| 漂移类型 | 含义 |
| --- | --- |
| `NEW_CHECK` | 当前运行出现基线中不存在的 Check |
| `MISSING_CHECK` | 基线 Check 在当前运行中消失 |
| `STATUS_CHANGED` | PASSED/FAILED 状态变化 |
| `RESULT_CHANGED` | Check 结果摘要发生变化 |
| `EVIDENCE_CHANGED` | Check Evidence 指纹发生变化 |

`durationMs`、`latencyMs` 以及包含临时候选 Bundle 身份的 `manifestChecksum` 在计算指纹前剔除，不造成
虚假漂移；BUNDLE_PREVIEW 仍比较运行时兼容范围、Mapping Plan 数和 Secret Reference 数。HTTP 状态、响应体校验和、
断言实际值校验和、映射输出和诊断仍参与比较。

## 5. 数据库

V26 新增：

- `tpip_verification_baseline`：不可变基线；一个来源 VerificationRun 只能创建一次；
- `tpip_verification_drift_report`：不可变漂移报告；同一基线与回归运行只能比较一次。

两张表均通过外键绑定原 Workspace、FixtureSuiteVersion 和 VerificationRun，避免产生孤立证据。

V28 在上述不可变证据之上增加漂移确认、接受/忽略决策和后继基线血缘，详见
`docs/verification-drift-governance-v0.1.md`。

## 6. API 流程

### 6.1 从已通过的完整验证创建基线

```http
POST /control/v1/verification-baselines
X-Operator: local-owner
Content-Type: application/json

{"sourceVerificationRunId":24}
```

### 6.2 手动执行回归并立即比较

```http
POST /control/v1/verification-baselines/{baselineId}:run
X-Operator: local-owner
```

响应同时返回 `REGRESSION VerificationRun` 与 `VerificationDriftReport`。

### 6.3 比较一个已经完成的运行

```http
POST /control/v1/verification-baselines/{baselineId}:compare
X-Operator: local-owner
Content-Type: application/json

{"verificationRunId":25}
```

该运行必须属于相同 Workspace、使用相同 FixtureSuiteVersion，且不能处于 RUNNING。

### 6.4 查询

```text
GET /control/v1/verification-baselines?workspaceId={workspaceId}
GET /control/v1/verification-baselines/{baselineId}
GET /control/v1/verification-baselines/{baselineId}/drift-reports
GET /control/v1/verification-drift-reports/{reportId}
```

## 7. REMOTE_CALL 边界

回归运行遵循 REMOTE_CALL 原有全部门禁：默认关闭、IDEMPOTENT、环境/Host/Port 白名单、HTTPS、次数、大小和
超时限制。创建基线不会发起网络请求；只有显式调用 `:run` 才可能调用 Provider。

## 8. 后续演进

1. 在 RegressionPolicy v0.1 固定间隔基础上增加 Cron、时区、维护窗口和环境级最大并发；
2. 在 v0.1 人工接受/忽略基础上增加职责分离、批量聚合和升级策略；
3. 增加漂移事件聚合、抑制和升级策略；
4. 支持字段级忽略规则，但忽略规则必须版本化并进入基线 checksum；
5. 增加趋势统计和连续失败阈值，避免单次外部抖动直接触发高等级告警。

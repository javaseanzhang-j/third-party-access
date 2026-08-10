# 通知运营服务等级治理标准 v0.11

## 1. 范围

v0.11 在 v0.10 固定窗口告警闭环之上增加：

- 环境级、不可变版本的通知运营 SLO 策略；
- 显式 `DRAFT -> PUBLISHED -> SUPERSEDED` 发布流程；
- 有审计记录的维护窗口排期和取消；
- 维护期间告警抑制；
- 未确认 CRITICAL 告警超时升级；
- 活跃未确认告警的周期提醒；
- 多控制面实例下的环境级数据库串行化边界。

## 2. SLO 策略资产

每个环境可以维护多个不可变策略版本，但任一时刻只有一个 `PUBLISHED` 版本参与评估。新版本发布后，旧版本转为 `SUPERSEDED`，历史评估仍保留其阈值快照。

策略字段：

| 字段 | 含义 |
|---|---|
| `minimumOperationalAttempts` | 进入健康判断的最低样本量 |
| `warningMinimumSuccessRate` | WARNING 最低成功率 |
| `criticalMinimumSuccessRate` | CRITICAL 最低成功率 |
| `criticalEscalationAfter` | CRITICAL 未确认后的升级等待时间，`PT0S` 表示关闭 |
| `repeatNotificationAfter` | OPEN 告警重复提醒间隔，`PT0S` 表示关闭 |

内容规范化后计算 SHA-256。策略发布后不允许原地修改，变更必须创建新版本。

未发布环境策略时，系统继续使用应用配置中的默认阈值，以支持平滑升级。

## 3. 维护窗口

维护窗口按环境定义开始时间、结束时间和原因，最长 31 天。规则如下：

- 同一环境不允许存在时间重叠的 `SCHEDULED` 窗口；
- 窗口创建后不能修改，只能取消并重新创建；
- 评估窗口与维护窗口有任意重叠时，仍保存 Evaluation 健康事实；
- 维护期间不创建新告警，不发送周期提醒或 CRITICAL 升级；
- `HEALTHY` 评估仍可关闭已有告警，防止维护结束后遗留假活跃事故；
- 评估阈值快照保存 `alertSuppressed` 和 `maintenanceWindowId`，便于审计解释。

## 4. 升级与重复提醒

CRITICAL 告警保持 `OPEN` 且超过 `criticalEscalationAfter` 后，原子写入 `escalated_at` 并生成：

```text
TPIP_NOTIFICATION_OPERATIONS_ALERT_ESCALATED
```

活跃 `OPEN` 告警超过 `repeatNotificationAfter` 后，原子更新 `last_notified_at` 并生成：

```text
TPIP_NOTIFICATION_OPERATIONS_ALERT_REPEATED
```

确认后的告警不再升级或重复提醒。数据库条件更新保证多个调度实例只有一个实例获得发送资格，事件与状态变更仍在同一事务提交。

## 5. 多实例并发治理

V19 增加 `tpip_notification_operations_environment_guard`。创建策略版本、发布策略和排期维护窗口前，事务会锁定对应环境行。

该边界保证：

- 同一环境版本号不会被并发重复分配；
- 并发发布最终只有一个 PUBLISHED 策略；
- 维护窗口的重叠检查与插入在同一环境内串行执行；
- 不同环境仍可并行治理。

## 6. 控制面接口

```http
POST /control/v1/notification-operations-governance/policy-versions
GET  /control/v1/notification-operations-governance/policy-versions?environmentCode=default
POST /control/v1/notification-operations-governance/policy-versions/{id}:publish

POST /control/v1/notification-operations-governance/maintenance-windows
GET  /control/v1/notification-operations-governance/maintenance-windows?environmentCode=default
POST /control/v1/notification-operations-governance/maintenance-windows/{id}:cancel
```

所有写接口要求 `X-Operator` 并写入审计事件。

## 7. 权限安全边界

当前 `X-Operator` 是审计身份，不是可信认证凭证。工程目前没有 OIDC Issuer、网关签名身份或 Spring Security，因此 v0.11 不接受客户端自报角色来模拟 RBAC。

正式启用细粒度 RBAC 前必须提供可信身份来源，并至少定义：

- `notification-operations.viewer`
- `notification-operations.operator`
- `notification-operations.policy-publisher`
- `notification-operations.maintenance-manager`

策略发布建议要求独立发布角色，且生产环境可以进一步增加双人审批。该部分将在身份提供方确定后接入，不以不可信 Header 代替。

## 8. 数据库迁移

- V18：策略版本、维护窗口、告警升级和重复通知字段；
- V19：环境级治理并发锁。

## 9. 验证

- 全量 Maven 测试 154 项通过；
- MySQL 8.4 已成功迁移到 V19；
- 策略和维护窗口查询接口已在真实控制面验证；
- 自动化测试覆盖版本发布、维护窗口重叠拒绝、维护抑制、超时升级、重复提醒及幂等。

## 10. 下一阶段

v0.12 建议实施 Attempt 可验证归档：归档批次清单、对象校验和、回读验证、合规冻结和“只删除已验证归档批次”的受控清理状态机。真实 RBAC 可在提供 OIDC/网关身份参数后并行接入。

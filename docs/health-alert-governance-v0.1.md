# TPIP 健康告警与连续窗口治理 v0.1

## 1. 目标

单个异常窗口可能来自瞬时抖动，不能对所有第三方渠道都立即回滚。本阶段将普通异常和严重故障分开：

- 普通异常：连续 N 个相邻窗口不健康后执行自动回滚；
- 严重异常：达到 Critical 错误率或 P95 阈值后立即回滚；
- 每个异常、保护和恢复事实形成可查询告警，并通过事务 Outbox 等待外部通知投递。

## 2. 连续窗口规则

默认配置：

```text
minimumSamples                 100
maximumErrorRate               5.00%
maximumP95LatencyMs            2000
consecutiveUnhealthyWindows    2
criticalErrorRate              20.00%
criticalP95LatencyMs           5000
```

只有前一窗口 `windowEnd == 当前 windowStart` 且判定为 `UNHEALTHY` 才累加。以下情况重置连续性：

- `HEALTHY`；
- `INSUFFICIENT_DATA`；
- 窗口断档；
- Deployment 已结束或发生回滚。

严重阈值使用“达到即触发”，不受连续窗口数量限制。每条评估证据冻结当时的普通阈值、Critical
阈值、已观察连续窗口数和触发原因，后续修改配置不会改变历史决策。

## 3. 告警状态

```text
OPEN -> ACKNOWLEDGED -> RESOLVED
  \---------------------> RESOLVED
```

- 普通异常但未达到连续窗口门槛：`WARNING / TPIP_CANARY_UNHEALTHY_WINDOW`；
- 已自动回滚或达到严重阈值：`CRITICAL / TPIP_CANARY_AUTO_ROLLBACK`；
- WARNING 升级为 CRITICAL 时，旧告警以 `ESCALATED_TO_CRITICAL` 原因关闭，再打开独立 CRITICAL；
- 人工确认只表示值班人员已接手，不改变健康决策和 Deployment 状态；
- 后续 HEALTHY 窗口自动将当前 Deployment 的 OPEN/ACKNOWLEDGED 告警置为 RESOLVED。

接口：

```text
GET  /control/v1/deployments/{deploymentId}/health-alerts
POST /control/v1/deployments/{deploymentId}/health-alerts/{alertId}:acknowledge
X-Operator: <operator>
```

## 4. 事务通知 Outbox

评估、回滚、告警和通知 Outbox 在同一个控制面事务中提交，避免“数据库已经回滚但通知事件丢失”。
事件类型：

- `TPIP_HEALTH_ALERT_OPENED`
- `TPIP_HEALTH_ALERT_ACKNOWLEDGED`
- `TPIP_HEALTH_ALERT_RESOLVED`

Outbox 初始状态为 `PENDING`，不在控制面事务内直接调用邮件、IM 或 Webhook，避免外部通知故障
阻塞部署治理。独立 Worker 已通过受保护接口完成 Claim、租约恢复、指数退避、Dead Letter、人工
重放和 Webhook 投递，完整语义见 `notification-outbox-dispatcher-v0.1.md`。

## 5. 数据表

- `tpip_deployment_health_alert`：告警状态、严重等级、确认人与恢复时间；
- `tpip_notification_outbox`：通知事件、投递状态、尝试次数和错误摘要；
- V7 迁移为唯一结构来源。

## 6. 下一步

1. 增加钉钉、企业微信和邮件 `NotificationProvider` 实现。
2. 支持按 Operation、Provider、Environment 覆盖健康策略。
3. 将告警确认、死信重放接入组织 RBAC 和值班排班。
4. 增加 Dispatcher 指标、渠道级投递记录和归档策略。

# 通知运营自动化标准 v0.10

## 1. 目标

v0.10 把 v0.9 的只读运营汇总升级为可审计、可确认、可恢复的后台运营闭环：

- 按环境执行 UTC 对齐的固定窗口评估；
- 以数据库唯一约束保证多控制面实例最终幂等；
- 管理 `OPEN -> ACKNOWLEDGED -> RESOLVED` 告警生命周期；
- 告警状态和 Notification Outbox 在同一事务提交；
- 明确保留与归档边界，外部归档未验证前不删除投递证据。

自动化默认关闭，由部署环境显式启用。

## 2. 固定窗口流程

```text
Scheduler tick
  -> now - evaluationDelay
  -> 向下对齐 evaluationWindow
  -> 针对每个受管环境汇总 Attempt
  -> INSERT IGNORE Evaluation(environment, start, end)
  -> 仅创建成功的实例执行告警状态机
  -> Alert 与 Notification Outbox 同事务提交
```

默认每分钟检查一次，以 5 分钟为评估窗口，并等待 30 秒，使延迟写入的 Attempt 能进入正确窗口。评估结果保存当时的阈值快照，后续修改配置不会改变历史判断依据。

## 3. 多实例幂等

`tpip_notification_operations_evaluation` 对 `(environment_code, window_start, window_end)` 建立唯一约束。所有控制面实例都可以运行调度器，但只有成功插入窗口事实的事务能推进告警状态机；其他实例读取既有结果后结束。

该机制不依赖进程锁或 Redis 锁，数据库是窗口事实的最终仲裁者。

## 4. 告警状态机

| 当前评估 | 活跃告警 | 动作 |
|---|---|---|
| `INSUFFICIENT_DATA` | 任意 | 不改变告警 |
| `HEALTHY` | `OPEN/ACKNOWLEDGED` | 解析为 `RESOLVED` |
| `WARNING` | 无 | 创建 WARNING |
| `WARNING` | WARNING/CRITICAL | 保持现状，防止重复告警或降级抖动 |
| `CRITICAL` | 无 | 创建 CRITICAL |
| `CRITICAL` | WARNING | 先解析 WARNING，再创建 CRITICAL |
| `CRITICAL` | CRITICAL | 保持现状 |

告警事件类型：

- `TPIP_NOTIFICATION_OPERATIONS_ALERT_OPENED`
- `TPIP_NOTIFICATION_OPERATIONS_ALERT_ACKNOWLEDGED`
- `TPIP_NOTIFICATION_OPERATIONS_ALERT_RESOLVED`

事件聚合类型为 `NOTIFICATION_OPERATIONS_ALERT`，并携带环境、告警、评估、严重级别和状态。通知路由仍使用已发布的路由、模板及渠道资产。

## 5. 控制面接口

```http
GET /control/v1/notification-operations/summary?from=...&to=...&environmentCode=default
GET /control/v1/notification-operations/evaluations?environmentCode=default&limit=100
GET /control/v1/notification-operations/alerts?environmentCode=default&limit=100

POST /control/v1/notification-operations/alerts/{alertId}:acknowledge?environmentCode=default
X-Operator: operator-a
```

查询接口无副作用。确认操作校验告警所属环境，只允许 `OPEN` 告警被确认，并与确认通知事件同事务提交。

## 6. 数据模型

V17 包含：

- Attempt 增加 `environment_code`，支持环境级隔离汇总；
- `tpip_notification_operations_evaluation`：不可变窗口评估事实；
- `tpip_notification_operations_alert`：当前告警生命周期及审计时间。

Evaluation 不能通过 API 修改或删除。Alert 只开放确认和由评估流程触发的恢复，不开放任意状态覆盖。

## 7. 配置

```yaml
tpip.notification-delivery:
  operations-automation-enabled: false
  operations-automation-poll-interval: 1m
  operations-evaluation-window: 5m
  operations-evaluation-delay: 30s
  operations-environments: default
  attempt-online-retention: 90d
```

`operations-environments` 可由逗号分隔的环境列表注入。环境编码遵守 `[a-z][a-z0-9_-]{0,31}`。

## 8. 保留与归档边界

`attempt-online-retention` 表达在线查询目标，不代表当前版本会自动删除数据。实施清理前必须具备：

1. 已验证的对象存储或数据湖归档 Provider；
2. 分区/批次清单、条数、时间范围和 SHA-256 校验；
3. 归档写入、读取回放和审计抽检成功；
4. 只清理已归档且超过保留期的 Attempt；
5. 删除批次可追踪，并支持合规冻结。

因此 v0.10 只建立保留策略配置和架构边界，不执行自动删除，避免可靠性证据不可恢复地丢失。

## 9. 验证结果

- 全量 Maven 测试 151 项通过；
- 本地 MySQL 8.4 从 V16 成功迁移至 V17；
- 控制面启动成功，健康检查为 `UP`；
- Evaluation 与 Alert 查询接口在真实数据库上返回成功。

## 10. 下一阶段建议

v0.11 建议建设通知运营安全与服务等级：为控制面操作引入细粒度 RBAC，增加告警抑制/维护窗口和升级策略，并将 Attempt 归档 Provider、校验清单与受控清理作业正式落地。

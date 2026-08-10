# TPIP 通知 Outbox Dispatcher v0.1

> 本文保留 v0.1 基础状态机；当前渠道级接口、路由和数据模型以
> `notification-channel-delivery-v0.2.md` 为准。

## 1. 目标与边界

健康评估事务只负责写入 `tpip_notification_outbox`，不在控制面事务中调用 Webhook、邮件或 IM。
独立 Worker 通过控制面内部接口认领任务并调用通知 Provider，因此外部渠道故障不会阻塞部署回滚，
Worker 也不需要读取 MySQL。

v0.1 提供 Webhook Provider。企业微信、钉钉、邮件等后续渠道通过 `NotificationProvider` SPI 增加，
不改变 Outbox 状态机和控制面数据边界。

## 2. 状态机

```text
PENDING --claim--> CLAIMED --success--> DELIVERED
                       |
                       +--failure, attempts < maximum--> PENDING（延迟重试）
                       |
                       +--failure, attempts >= maximum--> DEAD_LETTER

DEAD_LETTER --manual replay--> PENDING
```

- Claim 在控制面事务内使用 `FOR UPDATE SKIP LOCKED`，支持多个 Worker 并行消费；
- Claim 时递增 `attempt_count`，并写入 `claimed_by`、`claimed_at`；
- 超过租约时间的 `CLAIMED` 任务允许其他 Worker 重新认领；
- 完成与失败上报必须匹配当前 `claimed_by`，旧租约持有者不能覆盖新状态；
- 重试采用 `baseDelay * 2^(attempt-1)`，并受 `maximumDelay` 上限约束；
- 人工重放仅允许 `DEAD_LETTER`，会清零尝试次数并写入 `NOTIFICATION_REPLAYED` 审计事件。

## 3. 一致性语义

Dispatcher 提供 **at-least-once** 投递，而不是 exactly-once。Webhook 已成功、但 Worker 在确认控制面前
崩溃时，租约恢复会产生重复投递。接收方必须使用以下稳定请求头实现幂等：

```text
X-TPIP-Event-Id: <outbox id>
X-TPIP-Event-Type: <event type>
Idempotency-Key: tpip-outbox-<outbox id>
```

v0.2 已通过独立 `NotificationDelivery` 消除多 Webhook 共用重试状态的问题，参见
`notification-channel-delivery-v0.2.md`。本节描述保留为 v0.1 历史语义。

## 4. 接口

Worker 内部接口使用工作负载 Bearer Token：

```text
POST /internal/v1/notification-outbox:claim
POST /internal/v1/notification-outbox/{id}:delivered
POST /internal/v1/notification-outbox/{id}:failed
```

运维接口：

```text
GET  /control/v1/notification-outbox?status=DEAD_LETTER&limit=100
POST /control/v1/notification-outbox/{id}:replay
X-Operator: <operator>
```

失败信息只保存稳定错误码，例如 `WEBHOOK_HTTP_503`、`WEBHOOK_IO_FAILURE`，不记录 Webhook URL、
Authorization、响应体或异常堆栈，避免凭证和第三方报文进入数据库或日志。

## 5. 配置

控制面：

```yaml
tpip:
  notification-delivery:
    claim-lease: 30s
    retry-base-delay: 5s
    retry-maximum-delay: 5m
    maximum-attempts: 5
    maximum-batch-size: 100
```

Worker（默认关闭）：

```yaml
tpip:
  notification-dispatcher:
    enabled: true
    control-plane-base-uri: http://tpip-control-plane:8080
    worker-id: notification-worker-prod-01
    poll-interval: 2s
    request-timeout: 5s
    batch-size: 20
    webhook-endpoints:
      - https://alert-gateway.example.com/tpip/events
```

`TPIP_HEALTH_AUTOMATION_TOKEN` 由 Secret Manager 同时注入控制面和 Worker；Webhook Bearer Token 使用
`TPIP_NOTIFICATION_WEBHOOK_TOKEN` 注入。生产环境只允许 HTTPS，`allow-http-webhooks` 仅供本地验证。
逗号分隔的 `TPIP_NOTIFICATION_WEBHOOK_ENDPOINTS` 可用于环境变量配置。

## 6. 数据结构

- V7 创建 `tpip_notification_outbox`，保存事件内容、状态、次数、可用时间和错误摘要；
- V8 增加 `claimed_by` 与 Claim 查询索引；
- MySQL 仅由 Control Plane 访问，Worker 通过受控 API 消费任务；
- 已成功记录当前保留用于审计，后续应配置按合规期限归档和清理策略。

## 7. 后续演进

1. 将静态工作负载 Token 升级为 OIDC/SPIFFE 短期身份，并对内部接口实施细粒度 RBAC；
2. 增加企业微信、钉钉、邮件 Provider，以及按事件/环境选择渠道的路由策略；
3. 增加 Claim、重试、死信数量、投递耗时等 Micrometer 指标和告警；
4. 将多端点 fan-out 拆为每个订阅独立投递记录，隔离各渠道重试和死信；
5. 增加 Outbox 分区、归档、保留期和批量运维能力。

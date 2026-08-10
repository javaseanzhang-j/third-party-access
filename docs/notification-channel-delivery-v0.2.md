# TPIP 通知渠道级投递治理 v0.2

> 本文保留 v0.2 的演进记录。Worker 提交渠道列表的过渡方案已由
> `notification-routing-assets-v0.3.md` 的控制面渠道/路由资产与 Delivery 快照替代。

## 1. 设计目标

v0.1 将多个 Webhook 放在同一个任务中顺序调用。后一个渠道失败会使整个任务重试，导致前面已经
成功的渠道重复收到通知。v0.2 将模型拆为两层：

- `NotificationOutbox`：事务内产生的不可丢失事件事实；
- `NotificationDelivery`：事件面向某个稳定 `channelCode` 的独立投递任务。

每个渠道独立 Claim、租约、重试、成功、死信和人工重放。一个渠道失败不会改变其他渠道已经成功的
状态，也不会要求其他渠道再次投递。

## 2. 路由物化

Worker Claim 时提交自身支持的 `channelCodes`。控制面第一次处理尚未路由的 Outbox 时，在同一个
MySQL 事务内锁定事件并为每个渠道创建唯一 Delivery：

```text
Outbox 81
  ├── Delivery 201 / ops-primary
  ├── Delivery 202 / audit-webhook
  └── Delivery 203 / wecom-oncall
```

唯一约束 `(outbox_id, channel_code)` 防止并发重复物化。路由一旦物化便是该事件的冻结快照；后续
修改 Worker 渠道配置只影响尚未物化的新事件。一个 Worker 集群中的实例必须使用相同的渠道代码
集合；发布时应将渠道配置作为受控部署配置整体滚动更新。

## 3. 聚合状态

Delivery 保持 v0.1 状态机：

```text
PENDING -> CLAIMED -> DELIVERED
                    -> PENDING（退避重试）
                    -> DEAD_LETTER
DEAD_LETTER -> PENDING（人工重放）
```

Outbox 只表示聚合结果：

- 存在 `PENDING/CLAIMED`：`PENDING`；
- 所有 Delivery 都成功：`DELIVERED`；
- 所有 Delivery 均结束且至少一个死信：`DEAD_LETTER`；
- 重放任一死信 Delivery：Outbox 恢复为 `PENDING`。

## 4. 接口

```text
POST /internal/v1/notification-deliveries:claim
POST /internal/v1/notification-deliveries/{deliveryId}:delivered
POST /internal/v1/notification-deliveries/{deliveryId}:failed

GET  /control/v1/notification-deliveries?status=DEAD_LETTER&limit=100
POST /control/v1/notification-deliveries/{deliveryId}:replay
```

Claim 请求包含 `workerId`、`channelCodes` 和 `batchSize`。完成/失败仍使用租约所有者栅栏。人工重放
写入 `NOTIFICATION_REPLAYED / NOTIFICATION_DELIVERY` 审计事件。

## 5. Webhook 渠道路由

渠道代码和地址按相同顺序成对配置：

```yaml
tpip:
  notification-dispatcher:
    webhook-channel-codes:
      - ops-primary
      - audit-webhook
    webhook-endpoints:
      - https://alerts.example.com/ops
      - https://audit.example.com/events
```

环境变量可使用逗号分隔列表：

```text
TPIP_NOTIFICATION_WEBHOOK_CHANNEL_CODES=ops-primary,audit-webhook
TPIP_NOTIFICATION_WEBHOOK_ENDPOINTS=https://alerts.example.com/ops,https://audit.example.com/events
```

渠道代码必须稳定且唯一。Webhook 幂等键升级为
`tpip-outbox-{eventId}-channel-{channelCode}`，同时发送 `X-TPIP-Delivery-Id` 和
`X-TPIP-Channel-Code`。企业微信、钉钉群机器人当前可作为普通 Webhook 渠道接入；需要其专有签名、
消息卡片或限流语义时，应实现独立 `NotificationProvider`。

## 6. 可观测性

Worker 从 v0.2 起提供 HTTP 管理端口，默认 `8082`：

```text
GET /actuator/health
GET /actuator/metrics
GET /actuator/prometheus
```

核心指标：

- `tpip_notification_claim_cycles_total{outcome,code}`；
- `tpip_notification_deliveries_claimed_total`；
- `tpip_notification_deliveries_total{channel,outcome,code}`；
- `tpip_notification_delivery_duration_seconds{channel,outcome}`。

指标标签只使用受控渠道代码和稳定错误码，不包含 URL、事件 ID、Delivery ID 或报文，避免高基数及
敏感信息泄漏。

## 7. 数据结构与边界

- V9 创建 `tpip_notification_delivery`；
- Control Plane 负责 MySQL、状态聚合和路由物化；
- Worker 只通过内部 API 获取 Delivery，仍不依赖 MySQL/Flyway/JDBC；
- Webhook Token 继续由运行环境注入，不进入 Delivery、Outbox、API 或日志。

## 8. 下一步

1. 将渠道定义和路由规则升级为控制面不可变版本资产，消除 Worker 配置作为路由来源的过渡限制；
2. 实现企业微信、钉钉专有 Provider、签名器和消息模板；
3. 增加按事件类型、严重级别、环境、Provider/Operation 的渠道选择策略；
4. 增加积压数量、最老任务年龄和死信数量 Gauge 及相应告警规则；
5. 为 Delivery 与 Outbox 增加归档和数据保留策略。

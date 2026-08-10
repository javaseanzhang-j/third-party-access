# TPIP 通知路由资产与执行快照 v0.3

## 1. 本阶段结论

通知渠道和路由已从 Worker 环境配置提升为控制面治理资产。配置流程统一为：

```text
创建稳定身份 -> 创建不可变 DRAFT 版本 -> 校验/发布 -> 路由匹配
-> 物化 Delivery 执行快照 -> Worker Claim -> SecretRef 解析 -> Provider 投递
```

Worker 不再提交 `channelCodes`，也不保存渠道地址或全局 Webhook Token。设计态版本发布后不可原地
修改；一次 Delivery 创建时冻结实际执行所需信息，后续渠道或路由升级不会改变在途任务。

## 2. 资产模型

### NotificationChannel

稳定身份包含 `channelCode/channelName/status/currentVersionId/rowVersion`。版本包含：

- `providerType`：模型预留 `WEBHOOK/WECOM/DINGTALK`；当前发布门禁只允许 Worker 已实现的 `WEBHOOK`；
- `endpointUri`；
- `authorizationSecretRef`；
- Provider 扩展 `configuration`；
- 规范化内容的 SHA-256 `contentChecksum`；
- `DRAFT/PUBLISHED` 生命周期和发布时间。

### NotificationRoute

稳定身份包含 `routeCode/routeName/status/currentVersionId/rowVersion`。版本包含优先级、事件类型集合、
精确的渠道版本 ID 集合及内容校验和。引用的是 `channelVersionId` 而不是可漂移的渠道当前版本。

同一事件命中多个路由时按 `priority -> routeVersionId -> channelCode` 处理；同一 `channelCode` 只生成
一条 Delivery，优先级最高的匹配胜出。数据库唯一约束 `(outbox_id, channel_code)` 提供最终防重。

## 3. 发布门禁

- 资产代码必须匹配 `[a-z][a-z0-9.-]{1,99}`；
- 生产端点只允许 HTTPS；本地验证需显式开启 `TPIP_NOTIFICATION_ALLOW_HTTP_CHANNEL_ENDPOINTS`；
- URI 禁止 user-info、query 和 fragment，防止令牌进入地址、日志或指标；
- Secret 只保存 `env://TPIP_SECRET_*` Reference，不保存原文；
- `configuration` 必须是 JSON Object，并先规范化再计算校验和；
- 路由事件类型必须是 `*` 或大写事件代码，集合自动排序且禁止重复；
- 路由发布时，其引用的每个渠道版本都必须已经发布；
- 只有 DRAFT 版本可以发布，已发布版本不允许原地更新。

## 4. Delivery 执行快照

V10 为每条 Delivery 增加：

```text
channel_version_id
provider_type
endpoint_uri
authorization_secret_ref
```

控制面在 Outbox 首次路由时从已发布 RouteVersion 和 ChannelVersion 复制这些字段。Worker 经内部 API
取得快照，按 `providerType` 选择 Provider，并在实际发送前再次校验端点协议和 URI 结构。

这个边界保证：

- 设计态变更只影响新物化任务；
- 在途任务重试仍使用创建时的同一端点和 Secret Reference；
- Worker 不依赖 JDBC/Flyway/MySQL；
- 密钥轮换可在相同 Reference 后完成，无需修改或重建 Delivery；
- 历史任务可以准确追溯到渠道版本。

## 5. API

```text
POST /control/v1/notification-channels
GET  /control/v1/notification-channels
GET  /control/v1/notification-channels/{id}
POST /control/v1/notification-channels/{id}/versions
GET  /control/v1/notification-channels/{id}/versions
POST /control/v1/notification-channels/{id}/versions/{versionId}:publish

POST /control/v1/notification-routes
GET  /control/v1/notification-routes
GET  /control/v1/notification-routes/{id}
POST /control/v1/notification-routes/{id}/versions
GET  /control/v1/notification-routes/{id}/versions
POST /control/v1/notification-routes/{id}/versions/{versionId}:publish
```

所有写操作要求 `X-Operator`。Worker Claim 请求从 v0.3 起只包含 `workerId` 和 `batchSize`。

## 6. 配置示例

```json
{
  "providerType": "WEBHOOK",
  "endpointUri": "https://notify.example.com/tpip/events",
  "authorizationSecretRef": "env://TPIP_SECRET_OPS_WEBHOOK_TOKEN",
  "configuration": {"connectTimeoutMs": 1000}
}
```

```json
{
  "priority": 10,
  "eventTypes": ["TPIP_HEALTH_ALERT_OPENED", "TPIP_HEALTH_ALERT_RESOLVED"],
  "channelVersionIds": [101, 102]
}
```

## 7. 数据库与验证结果

Flyway V10 创建四张资产表并扩展 Delivery 快照字段：

- `tpip_notification_channel` / `tpip_notification_channel_version`；
- `tpip_notification_route` / `tpip_notification_route_version`；
- `tpip_notification_delivery` 新增四个执行快照字段。

本地 MySQL 8.4 已成功从 V9 升级到 V10。端到端验证覆盖了渠道创建与发布、路由创建与发布、事件
匹配、Delivery 快照物化、Worker SecretRef 解析、真实 HTTP Webhook 投递，以及 Delivery/Outbox
聚合状态变为 `DELIVERED`。验证数据与临时进程已清理，V10 迁移保留。

## 8. 下一阶段建议

1. 增加渠道/路由停用、发布审批与环境隔离；
2. 将 Secret Resolver 扩展到 Vault、云 Secret Manager，并用 SPI 选择；
3. 实现企业微信、钉钉专有签名、消息模板与限流 Provider；
4. 为“无匹配路由”增加等待原因、积压 Gauge 和治理告警；
5. 增加路由条件 DSL，支持严重级别、环境、Provider、Operation 等结构化条件；
6. 增加 Delivery/Outbox 保留、归档与合规清理策略。

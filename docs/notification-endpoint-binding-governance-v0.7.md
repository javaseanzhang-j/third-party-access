# TPIP 通知 Endpoint 绑定与连通性治理 v0.7

## 1. 设计决策

TPIP 已经拥有通用 `ProviderEndpoint` 技术资产，它具备环境、不可变修订、协议、地址、HTTP 方法、内容类型、
超时、网络/TLS 配置、凭证元数据和发布生命周期。通知子系统不再创建一套重复的 Endpoint Profile，而是直接
复用已发布 Endpoint Revision。

这项决策明确了资产边界：

- `ProviderEndpoint` 描述“在哪里、通过什么协议调用”；
- `NotificationChannelVersion` 描述“以哪个通知 Provider、模板和凭证引用执行”；
- `NotificationRouteVersion` 描述“什么事件发送到哪些渠道”；
- `NotificationDelivery` 冻结最终执行快照。

## 2. 渠道绑定

创建渠道版本时可提供 `endpointRevisionId`：

```json
{
  "providerType": "WECOM",
  "endpointRevisionId": 42,
  "authorizationSecretRef": "env://TPIP_SECRET_WECOM_KEY",
  "configuration": { "rateLimitPerSecond": 20 },
  "templateVersionId": 18
}
```

控制面从 Endpoint Revision 解析并冻结完整 `endpointUri`。绑定门禁要求：

- Endpoint Revision 必须为 `PUBLISHED`；
- Endpoint 与 Channel 环境相同；
- HTTP 方法必须为 `POST`；
- Content-Type 必须为 `application/json`；
- Endpoint 不得绑定数据库 `credentialRefId`，通知凭证统一由 Channel 的 SecretRef 管理；
- WECOM/DINGTALK 继续执行 v0.6 官方域名门禁。

`endpointUri` 直接配置模式暂时保留，作为存量渠道兼容路径。新建生产渠道应优先绑定 Endpoint Revision。

## 3. 执行快照

V14 为 Channel Version 和 Delivery 增加 `endpoint_revision_id`：

```text
ChannelVersion.endpointRevisionId
  -> Delivery.endpointRevisionId + Delivery.endpointUri
  -> Worker 只使用冻结 endpointUri
```

即使 Endpoint 后续发布新修订，既有 Channel Version 和 Delivery 都不会漂移。切换地址必须创建新的渠道版本，
再发布引用该渠道版本的路由版本。

## 4. 安全连通性预检

```text
POST /control/v1/endpoints/{id}:probe
GET  /control/v1/endpoints/{id}/probes?limit=20
```

预检只允许已发布修订，执行以下操作：

```text
DNS 解析 -> TCP 连接 -> HTTPS 时执行 TLS 握手与主机名校验
```

预检不会：

- 发送 HTTP 业务方法；
- 携带 Credential 或 SecretRef；
- 读取模板和通知消息；
- 在结果中保存异常文本、IP 地址或证书内容。

结果只记录 `SUCCESS/FAILURE`、安全原因码和延迟，例如：

```text
CONNECTED
DNS_RESOLUTION_FAILED
CONNECT_TIMEOUT
TLS_HANDSHAKE_FAILED
CONNECTION_FAILED
```

网络操作在数据库事务外执行；结果与审计事件在独立短事务中保存，避免远程等待长期占用数据库连接。

## 5. 数据库迁移

Flyway V14：

- Channel Version 增加 Endpoint Revision 外键；
- Delivery 增加冻结的 Endpoint Revision 外键；
- 新增 `tpip_endpoint_probe_result` 保存最近预检证据；
- 历史渠道和 Delivery 保持外键为空，继续使用原 `endpoint_uri` 快照。

## 6. 配置流程

```text
创建 Provider/Contract
  -> 创建 Endpoint Revision
  -> 发布 Endpoint Revision
  -> 执行无凭证连通性预检
  -> 创建并发布 Template Version
  -> 创建绑定 Endpoint/Template 的 Channel Version
  -> 创建并发布 Route Version
  -> Outbox 路由物化并冻结 Delivery
```

连通性成功是上线证据，不等价于厂商鉴权成功。涉及真实凭证和真实厂商业务响应的验证应使用受控沙箱渠道、
专用测试消息和审批流程，不应由普通 TCP/TLS 预检隐式发送消息。

## 7. 下一阶段

建议 v0.8 实现运行可靠性策略：分布式熔断、Retry-After 回传、错误分类驱动的重试/死信决策，以及 Endpoint
健康状态对新 Delivery 路由的保护。租户配额应在租户模型正式进入事件和渠道边界后实施，避免用环境代码冒充租户。

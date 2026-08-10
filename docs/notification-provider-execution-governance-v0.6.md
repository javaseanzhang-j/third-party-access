# TPIP 通知 Provider 执行治理 v0.6

## 1. 目标

v0.6 把企业微信、钉钉的协议差异封装在 Provider 适配层，通用模板 DSL、Outbox 和调度内核不感知厂商细节：

- Provider 能力以代码绑定的技术资产公开；
- 模板发布前执行厂商消息结构门禁；
- 渠道配置和最终消息一起冻结到 Delivery；
- Worker 在执行时解析 SecretRef、注入凭证并完成钉钉签名；
- 厂商 HTTP/业务错误转换为安全、低基数错误码；
- Redis 原子计数器提供多 Worker 共享限流。

钉钉自定义机器人接入和安全配置以[钉钉开放平台官方文档](https://open.dingtalk.com/document/orgapp/custom-robot-access)
为协议依据。厂商文档变化时，应新增能力版本和兼容性测试，不应直接改变已冻结 Delivery 的语义。

## 2. Provider 能力资产

```text
GET /control/v1/notification-provider-capabilities
```

能力响应包含 `providerType/capabilityVersion/contentType/messageTypes/credentialPlacement/signingSupported`
和 TPIP 本地默认频控。该资产是 Worker 可执行代码的事实描述，因此由代码版本治理，不允许数据库配置声明一个
Worker 实际没有实现的能力。

| Provider | 消息类型 | 凭证注入 | 签名 |
|---|---|---|---|
| WEBHOOK | 任意 JSON | Authorization Header | 无 |
| WECOM | text、markdown | `key` Query | 无 |
| DINGTALK | text、markdown | `access_token` Query | 可选 HMAC-SHA256 |

## 3. 模板契约

所有 Provider 继续使用 v0.5 的受限占位符 DSL 和变量 Schema。额外发布门禁如下：

- WECOM `text`：必须存在 `text.content`；
- WECOM `markdown`：必须存在 `markdown.content`；
- DINGTALK `text`：必须存在 `text.content`；
- DINGTALK `markdown`：必须存在 `markdown.title/markdown.text`；
- `msgtype` 必须是静态字符串，不能通过运行时变量改变消息结构；
- 当前内容类型只允许 `application/json`。

## 4. 渠道配置

企业微信渠道：

```json
{
  "providerType": "WECOM",
  "endpointUri": "https://qyapi.weixin.qq.com/cgi-bin/webhook/send",
  "authorizationSecretRef": "env://TPIP_SECRET_WECOM_KEY",
  "configuration": { "rateLimitPerSecond": 20 }
}
```

钉钉渠道：

```json
{
  "providerType": "DINGTALK",
  "endpointUri": "https://oapi.dingtalk.com/robot/send",
  "authorizationSecretRef": "env://TPIP_SECRET_DING_ACCESS_TOKEN",
  "configuration": {
    "signingSecretRef": "env://TPIP_SECRET_DING_SIGNING_SECRET",
    "rateLimitPerSecond": 20
  }
}
```

控制面不接受原始凭证；`configuration` 只允许 Provider 白名单字段。企业微信与钉钉默认只允许官方主机，避免
Worker 把 Query 凭证发送给非预期服务。企业代理接入应在后续增加受治理 Endpoint Profile/域名白名单，
不能通过关闭校验绕过。

## 5. Delivery 冻结边界

V13 为 Delivery 增加 `provider_configuration`。路由物化时冻结：

```text
providerType
endpointUri
authorizationSecretRef
providerConfiguration
templateVersionId
messageContentType
messagePayload
```

Worker 不读取 Channel、Template 或数据库。钉钉 `signingSecretRef` 也只是冻结引用，原始 Secret 仅在执行瞬间
由 Worker Secret Resolver 获取。

## 6. 签名与凭证处理

- WECOM：解析 `authorizationSecretRef`，URL 编码后注入 `key`；
- DINGTALK：解析 Access Token 并注入 `access_token`；
- DINGTALK 加签：以当前毫秒时间戳和签名 Secret 计算 HMAC-SHA256，再 Base64 与 URL 编码；
- 请求体、日志、指标和错误描述均不包含原始凭证；
- Endpoint 仍执行 HTTPS、无预置 Query、无 UserInfo 等安全校验。

## 7. 限流与错误归一

Worker 使用 Redis Lua 原子执行 `INCR + PEXPIRE`，Key 维度为 Provider、稳定 Channel 和 UTC 秒窗口。
因此多个 Worker 共享同一配额。Redis 不可用时返回 `PROVIDER_RATE_LIMIT_UNAVAILABLE` 并安全失败，不允许
绕过限流继续向厂商发送。

典型归一错误：

```text
WECOM_CREDENTIAL_REJECTED
WECOM_REMOTE_RATE_LIMITED
WECOM_REMOTE_REJECTED
DINGTALK_SECURITY_POLICY_REJECTED
DINGTALK_CREDENTIAL_REJECTED
DINGTALK_REMOTE_RATE_LIMITED
DINGTALK_REMOTE_REJECTED
PROVIDER_RATE_LIMIT_UNAVAILABLE
```

响应中的厂商 `errmsg` 不写入 Delivery 错误、日志或指标，避免敏感信息和高基数标签。

## 8. 数据库迁移

Flyway V13 为 `tpip_notification_delivery` 增加 JSON 合法性受约束的 `provider_configuration` 快照字段。
已有 Delivery 允许为空，Worker 将其解释为 `{}`，保持向后兼容。

## 9. 下一阶段

建议 v0.7 进入通知运营与可靠性增强：Provider Endpoint Profile、受治理域名白名单、熔断、Retry-After、
租户级配额、厂商沙箱连通性测试，以及凭证轮换不修改已发布资产的双引用策略。

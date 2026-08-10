# 通知运行时可靠性治理标准 v0.8

## 1. 目标

v0.8 在通知渠道、模板、路由和 Endpoint 修订均已冻结的基础上，建立统一的失败决策和端点保护机制：

- Worker 只采集安全错误码和可选 `Retry-After`，不自行决定最终死信；
- 控制面是重试与死信决策的唯一权威；
- 多 Worker 通过 Redis 共享端点熔断状态；
- 投递记录保留失败类别、实际重试延迟和死信时间；
- 所有可靠性状态均不得包含 URL、Secret 原文或第三方响应正文。

## 2. 运行流程

```text
Claim frozen delivery
  -> circuit permit by endpointRevisionId
  -> provider HTTP call
  -> success: reset consecutive endpoint failures
  -> failure: normalize safe code + parse Retry-After
  -> report to control plane
  -> classify failure
       PERMANENT    -> DEAD_LETTER immediately
       TRANSIENT    -> exponential retry, then DEAD_LETTER at maximum attempts
       RATE_LIMITED -> max(exponential delay, governed Retry-After)
       CIRCUIT_OPEN -> retry after remaining open duration
```

控制面不会信任第三方正文或 Worker 提供的“是否重试”结论，只根据规范化错误码执行统一分类。

## 3. 失败分类标准

| 类别 | 典型错误 | 行为 |
|---|---|---|
| `PERMANENT` | 认证拒绝、安全策略拒绝、配置/序列化错误、HTTP 4xx（排除 408/425/429） | 立即死信 |
| `RATE_LIMITED` | Provider 限流、HTTP 429 | 使用指数退避与 `Retry-After` 的较大值 |
| `CIRCUIT_OPEN` | 端点熔断已打开 | 使用 Redis 剩余 TTL 作为重试提示 |
| `TRANSIENT` | IO、超时、HTTP 408/425/5xx、未知安全错误码 | 指数退避，达到最大次数后死信 |

未知错误默认按 `TRANSIENT` 有界重试，既避免一次未知故障直接丢弃，又受最大尝试次数保护。

## 4. Retry-After 治理

Worker 支持 HTTP 标准的两种形式：秒数和 RFC 1123 日期。解析失败时忽略该头，不保存原始值。

控制面计算：

```text
effectiveDelay = max(exponentialDelay, min(retryAfter, maximumRetryAfter))
```

永久错误的重试延迟固定为零。默认 `maximumRetryAfter=1h`，防止异常第三方值无限期冻结投递。

## 5. 分布式端点熔断

熔断维度优先使用 `endpointRevisionId`；兼容直接 URI 的旧渠道时使用 `channelVersionId`。Redis Key 不包含端点 URI。

- 默认连续失败阈值：5；
- 默认统计窗口：1 分钟；
- 默认打开时间：30 秒；
- 仅 IO、响应解析异常、HTTP 408/425/5xx 计入端点可用性失败；
- 认证、配置和限流错误不计入端点熔断；
- 任一成功投递清除该端点修订的连续失败计数和打开状态；
- Redis 不可用时记录安全日志并放行，由数据库有界重试继续保护，避免形成全局通知中断。

当前采用 `CLOSED/OPEN` 两态与 TTL 自动恢复。半开探针的严格并发许可可以在后续结合流量规模增加。

## 6. 数据模型

V15 为 `tpip_notification_delivery` 增加：

- `failure_class`：最后一次规范化失败类别；
- `last_retry_delay_ms`：控制面实际采用的延迟；
- `dead_lettered_at`：进入死信的时间；
- `(delivery_status, dead_lettered_at)` 索引用于运营查询。

人工重放成功受理后清空以上失败状态；成功投递同样清空失败状态。冻结的渠道版本、Endpoint 修订和消息正文不被修改。

## 7. 配置项

控制面：

```yaml
tpip.notification-delivery:
  retry-base-delay: 5s
  retry-maximum-delay: 5m
  maximum-retry-after: 1h
  maximum-attempts: 5
```

Worker：

```yaml
tpip.notification-dispatcher:
  circuit-failure-threshold: 5
  circuit-failure-window: 1m
  circuit-open-duration: 30s
```

## 8. 安全与可观测性边界

- 失败接口只允许最长 100 位的 `[A-Z0-9_]+` 安全错误码和 0～86400 秒的重试提示；
- 不上传第三方响应正文、Header 集合、URI 或 Secret；
- Redis 日志只输出 Endpoint 修订 ID 或 Channel Version ID；
- 死信仍通过既有控制面接口查询和人工重放；
- `NotificationDelivery` 是事实记录，熔断状态是可丢失、可重建的运行时保护状态。

## 9. 后续建议

v0.9 建议建设通知运营面：按 Provider/Endpoint/Channel 的成功率与死信趋势、告警规则、批量死信处置、熔断状态只读查询，以及受控的半开探针。

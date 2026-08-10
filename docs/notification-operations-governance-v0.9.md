# 通知运营治理标准 v0.9

## 1. 目标

v0.9 将通知可靠性能力提升为可运营能力，覆盖：

- 不可变投递尝试证据；
- Provider、Endpoint Revision、Channel 三维运营汇总；
- 最低样本量和成功率阈值判定；
- 批量死信重放及审计；
- 熔断状态只读查询；
- 单许可半开恢复，避免恢复瞬间的并发冲击。

## 2. Attempt 事实模型

`NotificationDelivery` 是当前投递状态，`NotificationDeliveryAttempt` 是追加式运营事实。每次 Worker 的成功或失败回报都在同一数据库事务中写入 Attempt：

```text
Delivery claimed
  -> Worker executes frozen snapshot
  -> control plane accepts success/failure report
  -> update Delivery current state
  -> append DeliveryAttempt evidence
```

Attempt 冻结以下信息：

- `deliveryId` 和当前重放周期内的 `attemptNo`；
- `providerType/channelCode/endpointRevisionId`；
- `SUCCESS/FAILURE`；
- 安全 `errorCode/failureClass`；
- 实际采用的 `retryDelayMs`；
- 是否为终结失败；
- 发生时间。

人工重放会把 Delivery 的计数重新置零，但不会删除历史 Attempt，因此同一个 `attemptNo` 可以出现在不同重放周期，事实主键以自增 `id` 为准。

## 3. 运营汇总

接口：

```http
GET /control/v1/notification-operations/summary
    ?from=2026-08-01T00:00:00Z
    &to=2026-08-02T00:00:00Z
    &providerType=WEBHOOK
    &channelCode=ops-primary
    &endpointRevisionId=42
```

查询窗口必须大于零且不超过 31 天。过滤项均可省略。响应包含总尝试数、成功、失败、死信、成功率、健康状态，以及 Provider/Channel/Endpoint Revision 组合明细。

默认健康判定：

| 条件 | 状态 |
|---|---|
| 尝试数少于 20 | `INSUFFICIENT_DATA` |
| 成功率低于 95% | `CRITICAL` |
| 存在死信或成功率低于 99% | `WARNING` |
| 其余 | `HEALTHY` |

阈值由控制面环境配置治理。汇总是事实查询，不在 GET 请求内产生告警副作用；定时告警自动化应消费该标准结果，而不是复制 SQL。

## 4. 批量死信处置

```http
POST /control/v1/notification-deliveries:batch-replay
X-Operator: operator-a
Content-Type: application/json

{"deliveryIds":[7,8,9]}
```

规则：

- ID 必须唯一、为正数，默认最多 100 条；
- 所有目标必须处于 `DEAD_LETTER`；
- 在一个事务内完成，任一目标不符合规则则整体回滚；
- 生成逐条重放审计和批次审计；
- 只重置运行状态，不修改冻结渠道、Endpoint、模板或消息内容；
- 历史 Attempt 永久保留。

## 5. 熔断状态查询

```http
GET /control/v1/notification-circuits/ENDPOINT_REVISION/42
GET /control/v1/notification-circuits/CHANNEL_VERSION/9
```

状态包括：

- `CLOSED`：正常放行；
- `OPEN`：拒绝并返回剩余打开时间；
- `HALF_OPEN_READY`：等待一个恢复探针；
- `HALF_OPEN_IN_FLIGHT`：已有 Worker 获得探针许可；
- `UNAVAILABLE`：Redis 状态不可读。

接口只读，不允许操作人员直接清除熔断状态，避免绕过自动保护。

## 6. 单许可半开恢复

达到失败阈值时，Redis Lua 原子写入 `open` 与 `recovery` 状态。`open` 到期后：

1. 第一个 Worker 使用 `SET NX PX` 获得半开探针租约；
2. 其他 Worker 继续收到 `ENDPOINT_CIRCUIT_OPEN` 和探针剩余 TTL；
3. 探针成功则清除失败、打开、恢复和探针状态；
   收到 401、429 或厂商业务错误等明确响应也视为“端点可达”，仅关闭可用性熔断，通知本身仍按失败分类处理；
4. 探针失败则立即重新打开熔断，无需再次累计到阈值；
5. 探针 Worker 崩溃时，短租约到期后允许下一个 Worker 接管。

默认探针租约为 10 秒。Redis Key 仍只包含 Endpoint Revision ID 或 Channel Version ID，不包含 URL 和 Secret。

## 7. 数据库迁移

V16 创建 `tpip_notification_delivery_attempt`，包含外键、结果约束，以及时间、渠道、Provider、Endpoint Revision 查询索引。该表是追加事实，不提供修改或删除 API。

## 8. 配置

```yaml
tpip.notification-delivery:
  minimum-operational-attempts: 20
  warning-minimum-success-rate: 99.00
  critical-minimum-success-rate: 95.00

tpip.notification-dispatcher:
  circuit-half-open-probe-lease: 10s
```

## 9. 后续建议

v0.10 建议增加后台定时运营评估：按环境执行固定窗口汇总，生成可确认/恢复的通知运营告警，通过 Outbox 通知值班渠道，并增加 Attempt 数据保留与归档策略。

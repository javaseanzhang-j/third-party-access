# TPIP 通知环境与路由运营治理 v0.4

## 1. 目标

v0.4 在渠道/路由不可变版本和 Delivery 快照基础上增加运营治理能力：

- 渠道、路由和事件按 `environmentCode` 强隔离；
- 稳定身份支持乐观锁启用/停用；
- 没有匹配路由的事件不再被无限扫描，显式进入 `NO_MATCH`；
- 新路由发布、路由重新启用或人工操作可触发重路由；
- 控制面暴露未匹配事件查询 API 和 Prometheus Gauge。

## 2. 环境隔离

`NotificationChannel`、`NotificationRoute` 和 `NotificationOutbox` 都携带环境代码，格式为
`[a-z][a-z0-9_-]{0,31}`。稳定身份唯一键调整为：

```text
(environment_code, channel_code)
(environment_code, route_code)
```

路由物化要求 Outbox、Route 和 Channel 三方环境完全一致。路由发布门禁也会拒绝引用其他环境的
渠道版本。因此，即使 `eventType` 相同，`test` 路由也不可能消费 `prod` 事件。

部署健康告警产生通知事件时，环境来自 `IntegrationDeployment.environmentCode`，而不是使用默认值。
其他未来事件生产者也必须在事务内显式写入业务发生环境。

## 3. 启停语义

渠道和路由稳定身份允许在 `ACTIVE/INACTIVE` 之间切换，命令必须提供当前 `rowVersion`：

```text
POST /control/v1/notification-channels/{id}:status
POST /control/v1/notification-routes/{id}:status
```

- `INACTIVE` 只阻止新的路由物化；
- 已生成的 Delivery 保持冻结快照并继续重试，不受设计态启停影响；
- 重新启用路由或渠道后，同环境 `NO_MATCH` 事件重新变为 `UNROUTED`；
- 并发提交过期 `rowVersion` 会被拒绝；
- 每次状态变化都写入审计事件。

## 4. 路由状态机

V11 为 Outbox 增加独立于投递状态的路由状态：

```text
UNROUTED -> ROUTED
         -> NO_MATCH
NO_MATCH -> UNROUTED（新路由发布、资产重新启用或人工重路由）
```

`delivery_status` 继续表示 Delivery 聚合结果，`routing_status` 只表示是否找到执行目标。两者分离后，
运维人员可以区分“没有配置路由”和“已经路由但渠道投递失败”。

查询及人工重路由：

```text
GET  /control/v1/notification-routing-failures?limit=100
POST /control/v1/notification-routing-failures/{eventId}:reroute
```

人工重路由写入 `NOTIFICATION_REROUTE_REQUESTED` 审计事件。重路由不会修改事件事实、Payload 或已发布
资产，只会把 `NO_MATCH` 恢复为 `UNROUTED`。

## 5. 可观测性

控制面新增：

```text
tpip_notification_routing_unmatched
```

该 Gauge 表示当前 `NO_MATCH` Outbox 数量。控制面 Actuator 开放 `metrics/prometheus`，指标不携带
事件 ID、Payload 或动态 URL，避免高基数和敏感信息泄露。明细通过治理 API 分页查看。

## 6. 数据库迁移

Flyway V11：

- 为渠道、路由增加 `environment_code` 并调整唯一键；
- 为 Outbox 增加 `environment_code/routing_status/routing_attempted_at`；
- 根据是否已有 Delivery 回填历史 Outbox 为 `ROUTED/UNROUTED`；
- 增加未路由扫描索引和路由状态检查约束。

## 7. 验证结果

本地验证使用同一事件类型分别创建 `test` 和 `prod` 两个事件，仅配置 `test` 路由：

- `test` 事件成功生成测试渠道 Delivery；
- `prod` 事件进入 `NO_MATCH` 并可通过治理 API 查询；
- 停用测试路由后，新测试事件进入 `NO_MATCH`；
- 重新启用路由后，该事件自动回到 `UNROUTED` 并成功生成 Delivery；
- 全量自动化测试通过。

## 8. 下一阶段

建议进入通知内容治理：通知模板稳定身份/不可变版本、多语言变量 Schema、模板发布门禁，以及企业微信、
钉钉专有 Provider 的签名、限流和消息格式适配。

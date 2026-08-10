# TPIP Runtime 可观测性与健康治理 v0.1

## 1. 目标与边界

本阶段把 Runtime 执行事实转换为可观测信号，并将经过聚合的窗口指标作为 Deployment 健康保护输入。
观测事件只记录身份、阶段、耗时和结果，不记录 Canonical/Provider 报文、HTTP Header、Secret、Token
或 Policy 参数。

## 2. Runtime 指标

| 指标 | 类型 | 主要标签 |
| --- | --- | --- |
| `tpip.runtime.invocations` | Counter | operation、environment、deployment、bundleVersion、outcome、code |
| `tpip.runtime.invocation.duration` | Timer | operation、environment、deployment、bundleVersion、outcome |
| `tpip.runtime.stage.duration` | Timer | operation、stage、outcome |
| `tpip.runtime.provider.responses` | Counter | operation、provider、deployment、statusClass |

`requestId`、`traceId`、tenant、URL、异常消息和第三方业务码不能作为 Metric Tag，防止高基数和敏感信息
进入指标系统。Bundle/Deployment 标签来自受治理资产，阶段和结果码是固定枚举。

Prometheus 抓取入口为 `GET /actuator/prometheus`。Spring Actuator 原生 HTTP 指标同时覆盖
Bundle/路由解析前失败的请求；TPIP 自定义指标将解析失败归类为 `BUNDLE_*` 结果码。

## 3. 安全审计事件

每次已接收调用产生一个 `TPIP_RUNTIME_INVOCATION_COMPLETED` JSON 日志，Logger 名称为
`tpip.audit.runtime`。事件只包含：

```text
observedAt, instanceId, requestId, traceId, operationCode, environmentCode,
deploymentCode, bundleVersion, providerCode, providerStatusCode,
success, resultCode, durationMs, stages
```

审计序列化或输出异常不得影响业务调用。生产环境应将该 Logger 路由到独立的不可变审计管道，并按
组织规范处理 requestId/traceId 的保留期限和访问权限。

## 4. Runtime Readiness

`runtimeReadiness` HealthIndicator 汇总 Bundle 与 Deployment Route 缓存：

- `COLD`：尚未加载 Bundle，不等于故障；
- `READY`：至少一个 Bundle 已加载且没有 LKG 降级；
- `DEGRADED`：Bundle 或 Route 正在使用 `STALE_FALLBACK`。

Readiness 不访问设计态数据库。控制面不可用但 LKG 尚未过期时保持可服务并标记降级；超过 LKG
边界后，由实际解析失败和调用错误率触发告警。

Kubernetes/平台探针使用 `GET /actuator/health/readiness`，该命名分组包含 Spring
`readinessState` 和 `runtimeReadiness`。

## 5. Deployment 健康评估

控制面接口：

```text
POST /control/v1/deployments/{id}:evaluate-health
GET  /control/v1/deployments/{id}/health-evaluations
```

评估只接受 ACTIVE Canary Deployment，使用窗口开始/结束、样本数、失败数和 P95 延迟。错误率由控制面
根据计数重新计算，调用方不能直接提交计算结果。每次评估冻结当时的阈值和来源证据，写入
`tpip_deployment_health_evaluation`。

默认阈值：

```text
minimumSamples       100
maximumErrorRate     5.00%
maximumP95LatencyMs  2000
autoRollbackEnabled  true
consecutiveUnhealthyWindows  2
criticalErrorRate            20.00%
criticalP95LatencyMs         5000
```

决策：

- 样本不足：`INSUFFICIENT_DATA / NONE`；
- 满足门禁：`HEALTHY / NONE`；
- 错误率或 P95 超阈值：`UNHEALTHY`；
- 普通 `UNHEALTHY` 必须连续达到配置窗口数才触发保护；窗口必须在时间上严格相邻；
- 错误率或 P95 达到严重阈值时，不等待连续窗口，立即触发保护；
- 触发保护后创建新的不可变回滚 Deployment，记录 `AUTO_ROLLBACK` 和 `rollbackDeploymentId`。

平台不直接降低 Canary 流量，因为这会破坏“缩量必须显式回滚”和历史可审计原则。自动暂停在 v0.1
中实现为受审计的自动回滚：当前 Canary 进入 `ROLLED_BACK`，新 Deployment 以 100% 指向上一
不可变 Bundle。

## 6. 自动化 Worker

Worker 通过 Prometheus HTTP API 聚合样本数、失败数和 P95，使用 Redis 窗口锁协调多实例，通过
Bearer 工作负载令牌调用 `/internal/v1/deployment-health/**`。控制面按
`deployment_id + window_start + window_end` 唯一约束实现最终幂等。令牌至少 16 字符，Worker
关闭时允许不配置；生产环境应由 Secret Manager 注入并定期轮换。

详细流程与配置见 `health-automation-worker-v0.1.md`。

每个异常窗口会创建 WARNING 或 CRITICAL 告警；恢复窗口自动关闭未结束告警。告警打开、确认和恢复
均在同一事务写入通知 Outbox，详见 `health-alert-governance-v0.1.md`。

## 7. 下一步

1. 增加连续窗口策略，避免单个异常窗口直接回滚可容忍抖动的渠道。
2. 增加 OpenTelemetry Trace，并把 traceId 与审计事件关联。
3. 增加告警路由、值班确认和自动保护通知。
4. 将静态 Bearer Token 升级为短期工作负载身份和细粒度 RBAC。

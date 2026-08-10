# TPIP 健康评估自动化 Worker v0.1

## 1. 职责边界

自动化链路遵循三段式边界：Runtime 只产生运行事实，Worker 只取证和编排，Control Plane 持有
阈值、健康决策、回滚事务和审计。Worker 不连接设计态 MySQL，也不能自行修改流量。

```text
Runtime /actuator/prometheus
          |
          v
Health Worker -- Redis window lock/cooldown
          |
          v  Bearer workload token
Control Plane internal health API
          |
          v
Immutable evaluation + governed rollback
```

## 2. 执行流程

1. 按 `evaluation-window` 对 UTC 时间向下取整，生成稳定窗口。
2. 从控制面发现 `ACTIVE + previousDeploymentId != null` 的 Canary。
3. 尚未覆盖完整窗口的新 Canary 暂不评估。
4. 使用 `deploymentId + windowEnd` 获取 Redis 分布式锁，并检查部署冷却键。
5. 查询 Prometheus 的调用总数、失败数和 P95 Histogram。
6. 向控制面提交原始计数与来源证据，错误率仍由控制面计算。
7. 成功后进入冷却期；失败时使用 compare-and-delete 释放当前 Worker 持有的锁以允许重试。
8. 控制面依靠 V6 唯一键实现跨 Redis 故障和请求重放下的最终窗口幂等。

## 3. 内部接口

```text
GET  /internal/v1/deployment-health/candidates
POST /internal/v1/deployment-health/{deploymentId}:evaluate
Authorization: Bearer <workload-token>
```

令牌少于 16 字符、缺失或不匹配时返回 401。外部人工评估接口保留用于治理验证，但生产权限应与
内部自动化权限分离。

## 4. Prometheus 查询

Worker 使用受治理的 `deployment` 标签执行：

```promql
sum(increase(tpip_runtime_invocations_total{deployment="..."}[5m]))
sum(increase(tpip_runtime_invocations_total{deployment="...",outcome!="success"}[5m]))
histogram_quantile(0.95,
  sum by(le)(rate(tpip_runtime_invocation_duration_seconds_bucket{deployment="..."}[5m])))
```

Runtime 已显式开启调用耗时 Histogram。Prometheus 无数据返回 0，由控制面判定为
`INSUFFICIENT_DATA`，不会把监控空洞误判成健康。

## 5. 配置

控制面与 Worker 必须注入相同的 `TPIP_HEALTH_AUTOMATION_TOKEN`：

```yaml
tpip:
  health-worker:
    enabled: false
    control-plane-base-uri: http://tpip-control-plane:8080
    prometheus-base-uri: http://prometheus:9090
    poll-interval: 30s
    evaluation-window: 5m
    cooldown: 1m
    lock-ttl: 10m
    connect-timeout: 1s
    read-timeout: 5s
```

默认关闭 Worker，避免开发机未部署 Prometheus 时产生噪音。`lock-ttl` 不得短于评估窗口。

## 6. 下一版治理项

- 支持按渠道配置连续 N 个失败窗口再回滚；
- Prometheus 双副本/Thanos 查询和证据签名；
- OIDC/SPIFFE 短期工作负载身份替代静态 Token；
- 自动回滚告警、值班确认和事件升级；
- Worker 自身的成功率、积压、锁冲突和采集延迟指标。

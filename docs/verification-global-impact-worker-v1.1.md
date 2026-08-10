# Verification Global 影响任务 Worker v1.1

## 1. 目标与边界

v1.1 为 v1.0 的持久化分片任务增加受控后台执行器，使大量 Workspace 的影响计算可以由 Worker App
按批次持续推进。执行器默认关闭，只负责 `PENDING/RUNNING -> READY/FAILED` 的计算阶段；它不会自动重试失败项、
不会封板生成 Global Snapshot，也不会发布、激活或暂停治理策略。

本阶段仍是后端建设，不进入 UI。

## 2. 运行链路

```text
Worker 定时轮询
  -> Bearer Token 鉴权
  -> Control Plane 查询未过期 PENDING/RUNNING 任务
  -> Worker 按并发上限选择任务
  -> 本地批次限速
  -> 每个任务执行一个 v1.0 分片批次
  -> SKIP LOCKED + 租约保证多 Worker 并行安全
  -> 写入 Workspace Snapshot 和任务计数
  -> READY/FAILED 后停止被发现
  -> 人工评审、重试或封板
```

每次轮询对一个任务最多推进一个批次，避免单个大任务独占 Worker。多个 Worker 可同时运行；真正的任务项互斥仍由
v1.0 数据库租约承担，本地并发与限速只负责实例级资源治理。

## 3. 内部自动化契约

```text
GET /internal/v1/global-drift-policy-impact-jobs/runnable?limit=10
Authorization: Bearer {automation-token}

POST /internal/v1/global-drift-policy-impact-jobs/{jobId}:run-batch
Authorization: Bearer {automation-token}
X-Worker-Id: {stable-worker-id}
Content-Type: application/json

{"batchSize":20}
```

发现接口只返回未过期的 `PENDING/RUNNING` 任务，按创建时间稳定排序。令牌采用常量时间比较，长度不足 16 或不匹配
统一返回未授权。令牌只通过环境变量注入，不进入数据库、日志、证据或代码仓库。

## 4. 默认配置

```yaml
tpip:
  global-impact-worker:
    enabled: false
    control-plane-base-uri: http://127.0.0.1:18080
    worker-id: global-impact-worker-local-1
    poll-interval: 5s
    request-timeout: 30s
    expiry-warning: 5m
    batch-size: 20
    discovery-limit: 10
    max-concurrent-jobs: 2
    max-batches-per-minute: 30
```

启用示例：

```bash
TPIP_GLOBAL_IMPACT_WORKER_ENABLED=true
TPIP_HEALTH_AUTOMATION_TOKEN={至少16位的外部注入令牌}
TPIP_CONTROL_PLANE_BASE_URI=http://127.0.0.1:18082
TPIP_GLOBAL_IMPACT_WORKER_ID=global-impact-worker-local-1
```

约束：批次 1—100、发现数 1—50、并发任务 1—16、每分钟批次 1—600。配置非法时应用启动失败，避免执行器
以未治理参数运行。

## 5. 限流、并发和超时

- 固定大小虚拟线程执行池限制同一实例的并发任务数；
- 60 秒本地窗口限制调用批次数，超限时跳过并记录指标，下一轮自动恢复；
- HTTP 请求强制超时；轮询使用不可重入门闩，慢请求不会堆积重复调度；
- 当任务距过期时间小于 `expiry-warning` 时输出 `TPIP_GLOBAL_IMPACT_JOB_EXPIRING` 告警并累加指标；
- Worker 调用失败不自行改变任务状态，任务项租约到期后仍可被同一或其他 Worker 安全接管。

本地限速不提供集群总配额。若未来需要严格的跨实例吞吐预算，再升级为 Redis 令牌桶；当前数据库租约已经保证
正确性，因此 Redis 限流不可用不应阻断计算主链路。

## 6. 指标与日志

- `tpip.global.impact.worker.polls{outcome}`：轮询结果；
- `tpip.global.impact.worker.jobs.discovered`：发现任务数；
- `tpip.global.impact.worker.batches{outcome}`：批次结果；
- `tpip.global.impact.worker.items.claimed`：实际领取分片数；
- `tpip.global.impact.worker.batch.duration{outcome}`：批次耗时；
- `tpip.global.impact.worker.inflight`：当前执行批次数；
- `tpip.global.impact.worker.rate.limited`：实例限速次数；
- `tpip.global.impact.worker.jobs.expiring`：临期任务次数。

日志只记录 workerId、jobId、状态、数量和归一化错误信息，不记录 Token、策略正文、第三方报文或 Secret。

## 7. 操作流程

1. 操作人通过控制面创建 v1.0 Global 影响任务；
2. 确认 Control Plane 与 Worker 使用同一自动化令牌；
3. 显式启用 Worker，观察任务计数、失败率、耗时和临期告警；
4. 任务进入 FAILED 时由操作人分析并显式 `retry-failed`；
5. 任务进入 READY 后停止自动推进，由操作人检查覆盖和证据；
6. 操作人显式 `seal`，再由既有发布门禁消费快照；
7. 停用 Worker 只需将 enabled 设为 false 并重启，不影响已持久化任务。

## 8. 验收标准

1. 默认配置下 Worker 不访问控制面；
2. 缺少有效 Token 或参数越界时拒绝启动；
3. 发现接口不返回 READY、FAILED、SEALED、EXPIRED 或已过期任务；
4. 一轮最多处理 `max-concurrent-jobs` 个任务，每个任务只执行一个批次；
5. 限速窗口生效且 60 秒后恢复；
6. 多 Worker 并行时不重复完成同一任务项；
7. 请求失败、任务临期和限速均有日志/指标；
8. Worker 不触发 retry、seal、publish、activate 或 pause。

## 9. 后续演进

1. 增加任务取消、保留期清理和孤立证据治理；
2. 增加按 Workspace 风险、历史耗时和变化规模的动态分片；
3. 增加集群级吞吐预算、任务公平性与停滞 SLO；
4. 完成上述后端治理后，再评估任务监控与策略影响工作台 UI；进入 UI 前必须先执行既定提醒门禁。

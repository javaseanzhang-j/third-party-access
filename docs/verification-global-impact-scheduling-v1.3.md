# Verification Global 影响任务调度治理 v1.3

## 1. 目标与边界

v1.3 将 v1.1 的实例级轮询升级为控制面统一调度：优先级与老化公平性、集群并发预算、动态批次、调度租约、
任务项租约续期、故障接管、停滞 SLO、背压和恢复建议。

本阶段仍是后端能力，不进入 UI。调度器只推进 PENDING/RUNNING 任务；不自动重试失败任务、不取消任务、不封板、
不发布或激活策略。停滞监控默认关闭。

## 2. V40 调度状态

任务表新增：

- `job_priority`：LOW/NORMAL/HIGH/CRITICAL；
- `dispatch_lease_owner/dispatch_lease_until`：一个批次的短调度租约；
- `dispatch_count/last_dispatched_at`：公平性与调度审计事实；
- `last_progress_at`：任一任务项成功/失败时更新，是停滞 SLO 的观察点。

调度租约只保护“谁有权发起下一批”；任务项的五分钟租约仍保护具体 Workspace。两层租约分离，避免控制面
调度失效影响已经开始的原子计算。

## 3. 公平调度与防饥饿

调度顺序由“优先级权重 + 等待老化分”决定：

```text
priority weight: LOW=1, NORMAL=2, HIGH=3, CRITICAL=4
aging bonus: 每等待 5 分钟 +1，最大 +4
同分时按 lastDispatchedAt、createdAt、jobId 排序
```

每次领取使用 `FOR UPDATE SKIP LOCKED`，不同 Control Plane 实例不会保留同一调度租约。Worker 每轮对每个任务最多
执行一个批次，完成后释放调度租约。高优先级可以更快获得资源，但低优先级随等待时间增加最终会进入调度集合。

优先级调整必须匹配 rowVersion、提供原因并写 AuditEvent；SEALED/EXPIRED/CANCELLED 不允许调整。

## 4. 集群并发预算与背压

控制面在领取前统计未过期的其他 Worker 调度租约，并受 `maximum-active-dispatches` 限制。预算耗尽时返回空集合，
不让 Worker 在数据库和下游 Provider 已饱和时继续堆积请求。

Worker 仍保留实例级 `max-concurrent-jobs` 和 `max-batches-per-minute`。因此保护链路为：

```text
Control Plane cluster dispatch budget
  -> Worker instance concurrency
  -> Worker instance batch rate
  -> Job dispatch lease
  -> Workspace item lease
```

## 5. 动态批次

推荐批次由控制面返回，Worker 不再固定使用本地 batch-size：

1. 以剩余任务项和 `maximum-batch-size` 取上界；
2. 覆盖中存在 HIGH/CRITICAL Workspace 时减半；
3. 当前任务已完成项平均耗时达到 2 秒时再次减半；
4. 使用 minimum-batch-size 保护吞吐，但永不超过实际剩余项和 100 的协议上限。

动态批次只调整吞吐，不改变任务覆盖、快照时间、策略基线或证据语义。

## 6. 多 Worker、续期与故障接管

Worker 使用受 Bearer Token 保护的原子领取契约：

```text
POST /internal/v1/global-drift-policy-impact-jobs:claim-runnable
Authorization: Bearer {token}
X-Worker-Id: {unique-worker-id}
{"limit":10}
```

响应包含 jobId、状态、expiresAt 和 recommendedBatchSize。有效调度租约期间其他 Worker 不能领取同一任务；租约过期
后可以重新调度。执行接口校验 Worker 仍持有调度租约，并在批次结束后释放。

批次内每处理一个 Workspace 前，系统续期尚未处理的所有任务项租约。进程崩溃后不再续期，五分钟后其他 Worker
可通过 SKIP LOCKED 接管；旧 Worker 完成写入还必须匹配 leaseOwner，不能覆盖新 Worker 结果。

## 7. 停滞 SLO 与恢复建议

默认阈值：10 分钟 WARNING、30 分钟 CRITICAL。仅 PENDING/RUNNING 进入停滞查询。运行状态包含：优先级、调度次数、
最后调度/进度时间、当前租约、停滞秒数和恢复建议。

恢复建议：

- `START_OR_ENABLE_WORKER`：从未被调度；
- `WAIT_FOR_ACTIVE_WORKER`：仍有有效调度租约；
- `REDISPATCH_AFTER_LEASE_EXPIRY`：租约已失效，可安全重调度；
- `REVIEW_FAILURE_AND_RETRY`：存在失败项，等待人工评审；
- `READY_FOR_MANUAL_SEAL`：计算完成，等待人工封板；
- `NO_ACTION_TERMINAL`：已取消、过期或封板。

查询接口：

```text
GET .../global-governance-policy-impact-jobs/{jobId}/runtime-state
GET .../global-governance-policy-impact-jobs/stalled?limit=100
GET .../global-governance-policy-impact-jobs/slo-summary?limit=100
POST .../global-governance-policy-impact-jobs/{jobId}:reprioritize
```

## 8. 配置与指标

```yaml
tpip:
  global-impact-scheduling:
    dispatch-lease: 1m
    item-lease: 5m
    stall-threshold: 10m
    critical-stall-threshold: 30m
    maximum-active-dispatches: 8
    minimum-batch-size: 2
    maximum-batch-size: 50
    stall-monitor-enabled: false
```

指标：

- `tpip.global.impact.scheduling.dispatches{outcome}`；
- `tpip.global.impact.scheduling.jobs`；
- `tpip.global.impact.scheduling.backpressure`；
- `tpip.global.impact.scheduling.stalled{severity}`；
- 延续 v1.1 Worker poll、batch、items、duration、inflight、rate-limited 指标。

## 9. 验收标准

1. V40 成功迁移并通过约束校验；
2. 调度优先级、老化分和稳定次序防止长期饥饿；
3. 多控制面通过 SKIP LOCKED 领取，集群预算耗尽时背压；
4. 同一任务有效调度租约期间不能被其他 Worker 领取；
5. 调度租约过期后其他 Worker 可接管；
6. Worker 使用控制面推荐批次，风险和慢任务自动缩批；
7. 批次内任务项租约持续续期，旧 owner 不能覆盖新 owner；
8. 停滞查询、SLO 汇总、告警和恢复建议一致；
9. 优先级变更完整审计；
10. 全流程不自动重试、取消、封板或修改策略生命周期。

## 10. 下一阶段

v1.3 完成后进入 v1.4 UI 前置查询模型：稳定列表/详情 DTO、过滤分页、时间线、影响对比和操作能力描述。
v1.4 完成后下一阶段即为 UI；开始 UI 前必须执行既定提醒门禁。UI 首期按本地单用户模式建设，不包含登录页、
OIDC/JWT、RBAC、用户或角色管理；API Client 仅保留未来认证扩展点，并遵守
[`deferred-authentication-authorization-backlog.md`](deferred-authentication-authorization-backlog.md) 的本地安全边界。

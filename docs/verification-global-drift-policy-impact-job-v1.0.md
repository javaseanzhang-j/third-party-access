# Verification Global 策略影响异步分片任务 v1.0

## 1. 目标与边界

v1.0 将 v0.9 的同步聚合计算扩展为持久化、可分批、可恢复的任务资产。创建任务时冻结 Global 候选版本、
受影响 Workspace 集合及每个当前基线；执行端通过显式命令领取有限分片，全部成功后由人工封板生成可直接供
v0.9 发布/激活门禁使用的不可变聚合快照。

本阶段仍是后端能力，不进入 UI；默认不启用后台自动扫描，不自动重试、封板、发布或激活策略。同步 v0.9
接口继续保留给小规模覆盖，异步任务用于较大 Workspace 集合和需要运营追踪的场景。

## 2. 状态机

任务状态：

```text
PENDING -> RUNNING -> READY -> SEALED
                  \-> FAILED -> PENDING
PENDING/RUNNING/FAILED/READY -> EXPIRED
```

- `PENDING`：存在待领取分片；零 Workspace 任务直接进入 READY；
- `RUNNING`：已有成功/失败结果，但仍存在 PENDING/RUNNING 分片；
- `FAILED`：所有分片均结束且至少一项失败；
- `READY`：全部分片成功，可执行封板；
- `SEALED`：已生成不可变 Global 聚合快照，终态；
- `EXPIRED`：证据有效期结束，不能继续计算、重试或封板。

分片状态：

```text
PENDING -> RUNNING -> SUCCEEDED
                   \-> FAILED -> PENDING
RUNNING --lease expired--> RUNNING by another worker
```

成功分片不会因失败重试而重复计算；只重置 FAILED 分片。`attemptCount`、开始/完成时间及安全错误分类永久保留。

## 3. 持久化账本

V38 新增：

- `tpip_global_drift_policy_impact_job`：候选身份、coverage checksum、总数/成功/失败计数、状态、统一
  `snapshotAt`、有效期、封板快照 ID、乐观锁和操作人；
- `tpip_global_drift_policy_impact_job_item`：Workspace 顺序、冻结基线、状态、尝试次数、租约、子快照 ID、
  失败分类和处理时间。

任务项按 `(jobId, workspaceId)` 唯一，顺序在任务生命周期内稳定；成功子快照不可跨任务复用。账本和 v0.8/v0.9
证据使用外键连接，形成 `Job -> Item -> WorkspaceSnapshot -> GlobalSnapshot` 完整血缘。

## 4. API

创建任务：

```text
POST /control/v1/verification-drift-workbench/global-governance-policy-impact-jobs
X-Operator: {actor}

{"candidatePolicyId":1,"candidateVersionId":1,"ttlSeconds":3600}
```

查询任务和分页分片：

```text
GET /control/v1/verification-drift-workbench/global-governance-policy-impact-jobs/{jobId}
GET /control/v1/verification-drift-workbench/global-governance-policy-impact-jobs/{jobId}/items?page=0&size=20
```

显式执行一批：

```text
POST /control/v1/verification-drift-workbench/global-governance-policy-impact-jobs/{jobId}:run-batch
X-Worker-Id: {workerId}

{"batchSize":20}
```

`batchSize` 为 1—100。响应返回本次实际 `claimedCount` 和刷新后的任务计数/状态。

失败续算与封板：

```text
POST /control/v1/verification-drift-workbench/global-governance-policy-impact-jobs/{jobId}:retry-failed
X-Operator: {actor}
{"rowVersion":4,"reason":"provider facts restored"}

POST /control/v1/verification-drift-workbench/global-governance-policy-impact-jobs/{jobId}:seal
X-Operator: {actor}
{"rowVersion":7}
```

## 5. 分片领取与事务

- 领取使用 `FOR UPDATE SKIP LOCKED`，允许多个执行端并行消费同一任务；
- 每个分片持有 5 分钟租约，进程中断后其他执行端可领取过期 RUNNING 项；
- 本次领取身份由锁定的 Workspace ID 集合确定，不使用时间戳等非稳定字段回查；
- 完成更新必须同时匹配 `workerId`，过期执行端不能覆盖新执行端结果；
- 每个分片使用独立事务：子快照创建与 SUCCEEDED 原子提交；失败时先回滚计算事务，再以独立事务记录 FAILED；
- 所有子快照共用任务 `snapshotAt`，即使分批跨越时间，也保持同一影响观察点。

## 6. 失败与续算

当前安全错误分类：

- `BASELINE_CHANGED`：Workspace 当前有效策略与任务冻结基线不一致；
- `IMPACT_COMPUTATION_FAILED`：影响查询、证据写入或其他计算失败。

错误消息最多保存 500 字符，不保存堆栈、SQL、Provider 报文或 Secret。只有未过期 FAILED 任务可按 `rowVersion`
显式重试；重试原因进入 AuditEvent，成功项和既有子快照保持不变。

基线变化通常意味着整个 coverage checksum 已过时。即使失败项被重试成功，封板仍会重新计算覆盖；覆盖不一致时
必须放弃旧任务并创建新任务，不能通过重试绕过评审边界。

## 7. 最终封板

封板前必须满足：

1. 任务是未过期 READY 且 `rowVersion` 匹配；
2. 实时 Workspace 数量和 coverage checksum 与任务一致；
3. 分片数量完整且全部 SUCCEEDED；
4. 每个成功项引用的 v0.8 子快照存在；
5. 聚合快照保存成功后，任务在同一事务中转为 SEALED 并绑定快照 ID。

封板只生成治理证据，不发布或激活策略。后续命令继续由 v0.9 门禁完整校验并分别消费聚合快照。

## 8. 验收标准

1. 任务创建冻结候选、coverage checksum、Workspace 基线和统一观察时间；
2. 批量领取上限、稳定顺序、SKIP LOCKED 和租约恢复有效；
3. 领取响应精确返回本次 Workspace 集合；
4. 逐项成功/失败事务互不污染，计数和状态可对账；
5. FAILED 只重置失败项，记录原因并使用乐观锁；
6. READY 任务覆盖未漂移时可封板，生成 v0.9 兼容快照；
7. EXPIRED、覆盖漂移、证据缺失或并发版本冲突时拒绝封板；
8. 全流程不修改策略生命周期。

## 9. 后续演进

1. 在 Worker App 中增加默认关闭的受控拉取执行器、限速与指标；
2. 增加任务取消、保留期清理及孤立失败证据治理；
3. 增加按风险和变化量动态分片、并发度和超时预算；
4. 进入任务监控、影响对比或策略工作台 UI 前执行既定 UI 阶段提醒门禁。

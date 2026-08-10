# Verification Global 影响任务生命周期治理 v1.2

## 1. 目标与边界

v1.2 在 v1.0 任务账本和 v1.1 Worker 之上补齐任务退出与数据保留闭环：人工取消、到期收敛、保留期预览、
受控物理清理、孤立 Workspace Snapshot 识别以及不可变清理回执。

本阶段仍是后端治理，不进入 UI。到期扫描、任务物理清理和孤立快照删除均默认关闭；系统不会清理 SEALED 任务、
Global Snapshot、已被发布/激活消费的证据或仍被 Global Snapshot 引用的子证据。

## 2. 生命周期

```text
PENDING/RUNNING/FAILED/READY -> CANCELLED
PENDING/RUNNING/FAILED/READY --expiresAt--> EXPIRED
READY -> SEALED
CANCELLED/EXPIRED --retention elapsed--> PURGE RECEIPT + physical job removal
```

- `CANCELLED` 是不可恢复终态，必须提供原因和匹配 `rowVersion`；
- `EXPIRED` 由显式维护命令或默认关闭的扫描器落账，不依赖查询时临时计算；
- `SEALED` 永不进入本阶段清理候选；
- 任务清理后不再保留原任务行和任务项，但保留独立、不可变的 purge receipt 和 AuditEvent；
- 清理回执中的 `jobId` 唯一，防止同一任务产生两份清理事实。

## 3. 取消并发边界

取消只允许未过期的 `PENDING/RUNNING/FAILED/READY`。更新状态使用乐观锁，数据库领取 SQL 同时校验父任务仍为
`PENDING/RUNNING` 且未过期。即使 Worker 在取消前拿到旧的发现结果，后续也不能再领取分片。

已经持有租约的分片可能完成自己的原子事务，但任务刷新会保持 `CANCELLED`，不会恢复为 RUNNING/READY。保留期最短
为一天，远大于五分钟分片租约，因此不会删除仍在正常执行窗口内的数据。

## 4. 清理回执

V39 新增 `tpip_global_drift_policy_impact_job_purge_receipt`，永久保存：

- 原 jobId、候选策略/版本和两个 checksum；
- 终态、Workspace/成功/失败/任务项数量；
- 发现的孤立快照数和实际删除数；
- 清理原因、操作人和时间。

回执不保存 impact document、第三方报文、SQL 错误、堆栈或 Secret。任务删除和回执写入处于同一业务事务；回执
写入失败时任务与子证据删除一起回滚。

## 5. 孤立证据规则

任务项关联的 Workspace Snapshot 同时满足以下条件才标记为可删除孤立证据：

1. Snapshot 已过期；
2. `publish_used_at` 和 `activation_used_at` 均为空；
3. 不被 `tpip_global_drift_policy_impact_snapshot_item` 引用；
4. 所属任务已满足 CANCELLED/EXPIRED 和保留期门禁。

`deleteOrphanSnapshots=false` 时只在回执中记录发现数量，不删除 Snapshot。任务项删除后这些 Snapshot 会继续作为
独立证据保留，可由后续专门证据保留策略处理。

## 6. API

取消任务：

```text
POST /control/v1/verification-drift-workbench/global-governance-policy-impact-jobs/{jobId}:cancel
X-Operator: {actor}
{"rowVersion":3,"reason":"candidate withdrawn"}
```

预览保留期候选：

```text
GET /control/v1/verification-drift-workbench/global-governance-policy-impact-jobs/maintenance-candidates?retentionDays=7&limit=50
```

Dry Run 或正式维护：

```text
POST /control/v1/verification-drift-workbench/global-governance-policy-impact-jobs/maintenance:run
X-Operator: {actor}

{"dryRun":true,"expireDue":true,"deleteOrphanSnapshots":false,
 "retentionDays":7,"limit":50,"reason":"weekly retention review"}
```

Dry Run 不执行到期落账、任务删除或 Snapshot 删除，只返回当前已进入终态且超过保留期的候选。

任务清理后可按原 jobId 查询永久回执：

```text
GET /control/v1/verification-drift-workbench/global-governance-policy-impact-jobs/{jobId}/purge-receipt
```

## 7. 默认关闭的自动维护

```yaml
tpip:
  global-impact-job-maintenance:
    expiry-enabled: false
    purge-enabled: false
    delete-orphan-snapshots: false
    poll-interval: 1h
    retention: 7d
    batch-size: 50
```

建议启用顺序：先启用 expiry，观察至少一个保留周期；再启用 purge 但保持孤立快照删除关闭；对清理回执和恢复演练
验收后，才单独启用孤立快照删除。

## 8. 指标与审计

- `tpip.global.impact.jobs.expired`：完成到期落账数量；
- `tpip.global.impact.jobs.purge{outcome}`：物理清理结果；
- `tpip.global.impact.orphan.snapshots{action=found|deleted}`：孤立证据发现/删除数量；
- AuditEvent：`...JOB_CANCELLED`、`...JOB_EXPIRED`、`...JOB_PURGED`；
- 调度摘要日志：到期数、候选数、清理数、失败数和是否启用孤立删除。

## 9. 验收标准

1. 取消使用 rowVersion 和原因，只允许合法非终态；
2. 取消后陈旧 Worker 无法继续领取，刷新不能复活任务；
3. 到期扫描不修改 SEALED/CANCELLED；
4. 最短保留期一天，Dry Run 零写入；
5. 只有 CANCELLED/EXPIRED 可清理，清理与回执原子提交；
6. 被消费或被聚合引用的 Snapshot 永不作为孤立证据删除；
7. 自动到期、任务清理、孤立删除三项默认关闭；
8. 全流程不修改候选策略生命周期。

## 10. 后续演进

下一阶段建议建设任务公平调度、动态分片和停滞 SLO，仍可保持 API/后端优先。进入任务监控或影响对比 UI 前，
必须先执行既定 UI 阶段提醒门禁。

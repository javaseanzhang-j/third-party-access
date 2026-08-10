# VerificationBaseline 血缘与影响分析 v0.1

## 1. 目标与边界

本能力把已经持久化的基线血缘、漂移决策和 RegressionPolicy 版本引用转换为可查询的治理视图，回答三个
问题：当前基线从哪里演进而来、这条演进树发生过哪些漂移、哪些自动回归策略仍受它影响。

v0.1 只提供实时只读查询，不新增可变状态、不缓存统计结果，也不改变漂移接受、策略发布或策略激活流程。
查询结果不返回 Baseline Snapshot、Drift Report Document、Fixture 输入或 Provider 响应。

## 2. 血缘树

```http
GET /control/v1/verification-baselines/{baselineId}/lineage
```

服务先沿 `predecessorBaselineId` 找到根基线，再返回该根下面的完整演进树。每个节点包含深度、子节点数量、
被接受漂移报告和相对于请求基线的关系：

- `SELF`：请求基线；
- `ANCESTOR`：请求基线的祖先；
- `DESCENDANT`：请求基线的后继；
- `BRANCH`：同根但不在请求基线直接祖先/后继链上的旁支。

返回完整同根树而非只返回一条链，是为了让治理人员发现从同一事实基线产生的并行标准。树使用广度优先、
同层按 ID 排序，确保 API 输出稳定。缺失前驱或循环血缘会作为数据完整性错误拒绝；单棵树最多返回 1000
个节点，超过后应使用未来的离线图谱分析能力。

## 3. 漂移趋势

```http
GET /control/v1/verification-baselines/{baselineId}/drift-trend
```

趋势按血缘树中的每个基线聚合：报告总数、`NO_DRIFT` 数、`DRIFTED` 数、变化检查项总数，以及
`OPEN / ACKNOWLEDGED / ACCEPTED / DISMISSED` 决策数量和最近报告时间。顶层同时返回全树汇总。

趋势表示“已记录证据的治理分布”，不是时间序列监控指标。它不展开报告明细，也不推断漂移原因；需要调查
时仍通过原 DriftReport 与 VerificationCheck 的受控接口追溯证据。

## 4. RegressionPolicy 影响范围

```http
GET /control/v1/verification-baselines/{baselineId}/impact
```

影响视图返回整棵血缘树中所有被 RegressionPolicyVersion 引用的基线，并区分：

- 历史引用：不可变旧版本曾使用该基线；
- 当前引用：版本是策略的 `currentVersionId`；
- 活动调度：当前引用且策略状态为 `ACTIVE`。

每条引用包含策略身份、策略初始基线、策略状态、版本号、实际版本基线和版本状态。顶层统计受影响策略数、
引用版本数、当前引用数和活动调度数。影响分析不会把“历史存在”误判为“当前仍运行”。

## 5. 查询与性能设计

- 血缘使用 V28 的 `idx_verification_baseline_predecessor` 和 Workspace 索引；
- 漂移报告使用 V26 的 `(baseline_id, drift_status, created_at)` 索引；
- 策略版本使用 V29 的 `baseline_id` 索引；
- 漂移报告、治理记录和策略版本均按 ID 集合批量读取，避免逐节点、逐报告或逐策略 N+1；
- 当前数据结构和索引已经满足 v0.1，不新增 V30 数据库迁移。

## 6. 一致性与安全

三个查询运行在只读事务中，以一次请求期间的数据库已提交状态为准。资产本身仍遵循不可变版本规则；策略
当前版本和状态可能在并发请求后发生变化，因此影响结果是查询时刻快照，不作为发布或激活命令的授权依据。

当前本地单人模式不启用 OIDC/RBAC。未来接入统一认证后，血缘和汇总趋势可授予资产查看者，证据详情与包含
人员决策说明的接口应单独授权。本 API 本身不返回决策说明、敏感报文、URL、Header 或 Secret。

## 7. 验收标准

1. 任意树节点都能找到正确根并返回完整同根树；
2. `ANCESTOR / SELF / DESCENDANT / BRANCH` 分类准确且顺序稳定；
3. 漂移状态和治理决策统计与原始记录一致；
4. 历史版本、当前版本和活动调度引用能够明确区分；
5. 不返回 Snapshot、Report Document 或执行证据；
6. 不创建审计事件、不修改资产或运行状态；
7. 不产生逐策略 N+1 查询，超过 1000 节点的树被明确拒绝。

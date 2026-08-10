# TPIP UI 漂移治理评估与执行资产 v0.14

## 1. 阶段目标

v0.14 将有效治理策略解析、逐报告抑制评估、提醒候选判断和已冻结执行账本接入 UI。页面只展示既有事实和
策略计算结果，不物化执行账本，不创建提醒批次、Notification Outbox 或投递任务。

## 2. 页面能力

新增 `/drift-governance-evaluations`，并以 Workspace 为强制范围：

- 展示命中的 Workspace、Global 或内置默认策略来源；
- 展示超期时限、聚合窗口、提醒间隔、提醒上限和负责人；
- 展示可行动报告、Review 状态、`rowVersion`、截止时间和超期结论；
- 明确区分“完全抑制”“存在有效漂移”和“提醒候选”；
- 展开查看逐漂移项 `kindSuppressed`、`checkSuppressed` 和最终抑制结论；
- 展示策略抑制类别与检查项清单；
- 展示既有执行账本状态、负责人、提醒预算、下次允许时间、聚合键和评估 checksum；
- 支持进入策略版本、运营度量和漂移治理工作台。

Workspace 详情新增“查看治理评估”入口，自动携带 `workspaceId`。

## 3. 查询契约

```text
GET /control/v1/verification-drift-workbench/governance-evaluations
GET /control/v1/verification-drift-workbench/governance-executions?workspaceId={workspaceId}
```

评估分页只返回可行动报告。执行账本接口只投影运营字段、聚合键和 checksum，不向 UI 返回内部策略快照文档与
评估快照文档。

## 4. 语义边界

```text
reminderCandidate = actionable && overdue && !fullySuppressed
```

- 单个漂移项命中类别或检查项任一规则即被抑制；
- 只有报告内全部漂移项被抑制，报告才是 `fullySuppressed`；
- “提醒候选”不是已物化、已批准、已提交或已发送；
- 执行账本 `READY` 只表示冻结上下文和预算，不能解释为通知已发送；
- 本页数量“本页候选”和“本页完全抑制”明确是分页内计数，不冒充全局汇总；
- 页面不提供 Materialize、批准、提交、替代或发送按钮。

## 5. 验收结果

```text
Test Files  20 passed (20)
Tests       36 passed (36)
vue-tsc / tsc / vite build passed
治理评估资产页 chunk 8.88 kB（gzip 3.27 kB）
最大 JS chunk 427.27 kB（gzip 145.71 kB）
Playwright 静态发现 10 个场景
```

真实 MySQL 8.4 环境对 Workspace 23 的只读验收结果：

- 策略来源 `BUILT_IN_DEFAULT`；
- 超期 72 小时、聚合窗口 1 天、提醒间隔 1 天、最多提醒 3 次；
- 当前可行动评估 0 条；
- 当前已冻结执行账本 0 条。

验收未物化执行账本、未创建通知或 Outbox。Control Plane 已优雅停止，18082 端口已释放；本阶段未启动浏览器。

## 6. 下一阶段建议

UI v0.15 建设提醒批次运营资产：只读展示 DRAFT、APPROVED、SUBMITTED、CANCELLED、REPLACED 生命周期、成员
差异、投递关联和 Workspace 提醒指标。写操作仍保持人工显式确认，并在真实候选测试夹具与可回滚验收方案完成后
再接入。

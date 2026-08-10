# TPIP UI 漂移治理运营度量 v0.13

## 1. 阶段目标

v0.13 把既有 Workspace 级治理指标和漂移聚合查询接入 UI，形成从运营态势到负责人、漂移簇和具体报告的
只读分析链路。本阶段不建立第二套指标事实，不新增数据库迁移，不启动扫描、提醒或自动处置。

## 2. 页面能力

新增 `/drift-governance-metrics`，以 Workspace 为强制查询范围：

- 支持 7、14、30、60、90 天 UTC 分析窗口；
- 默认解析 Workspace 有效治理策略中的 SLA；
- 支持显式 SLA 分析覆盖，但不修改策略或报告；
- 展示当前待处置、突破 SLA、分派覆盖率和未分派超期数；
- 展示窗口内处置数量、SLA 达标率、接受/驳回和处置耗时；
- 展示 Dry Run、正式应用、正式拒绝和命令成员质量；
- 展示服务端补零后的每日事实趋势；
- 展示负责人当前负载、超期数量和最老待办；
- 展示 `checkCode + driftKind` 漂移聚合簇，并钻取到工作台组合筛选结果。

Workspace 详情新增“查看运营度量”入口。工作台支持从 URL 恢复 Workspace、范围、漂移类型、检查项和负责人
筛选，因此运营页面钻取结果可以刷新和分享，不依赖页面内临时状态。

## 3. 查询契约

```text
GET /control/v1/verification-drift-workbench/governance-metrics
GET /control/v1/verification-drift-workbench/groups
```

指标接口负责解析有效策略 SLA。聚合查询使用指标响应中的 `slaHours` 计算超期数量，避免策略 SLA 与固定 72 小时
口径不一致。趋势、百分比和缺失日期均由服务端计算，前端不根据当前分页推断全局指标。

## 4. 指标边界

- Backlog 只统计 `OPEN/ACKNOWLEDGED`；
- Resolution 使用报告创建到解决的端到端时长；
- 正式成功率为 `APPLIED / (APPLIED + REJECTED)`，Dry Run 不进入分母；
- 日趋势是事实事件计数，不伪装成历史库存曲线；
- 未分派负责人保持独立空值语义，不伪造人员编码；
- 指标响应不包含处置说明、原始漂移证据、Provider 报文或 Secret；
- 页面不提供写命令，不产生 AuditEvent 或其他业务数据。

## 5. 验收结果

```text
Test Files  19 passed (19)
Tests       34 passed (34)
vue-tsc / tsc / vite build passed
治理运营度量页 chunk 10.93 kB（gzip 3.71 kB）
漂移治理工作台 chunk 10.60 kB（gzip 4.07 kB）
最大 JS chunk 427.27 kB（gzip 145.71 kB）
Playwright 静态发现 9 个场景
```

真实 MySQL 8.4 环境对 Workspace 23 的 30 天只读验收结果：

- 有效 SLA 72 小时，来源 `BUILT_IN_DEFAULT`；
- 当前待处置 0、超期 0；
- 窗口内解决 1、接受 1、SLA 达标率 100%；
- 命令 2：预检 1、正式拒绝 1、正式应用 0；
- 返回连续 30 个 UTC 日期，缺失日补零；
- 聚合簇 1 个：`BUNDLE_PREVIEW + EVIDENCE_CHANGED`。

验收未执行写命令。Control Plane 已优雅停止，18082 端口已释放；本阶段未启动浏览器。

## 6. 下一阶段建议

UI v0.14 建设治理评估与提醒执行资产页：先只读展示有效策略评估、抑制证据、提醒候选和既有执行账本；涉及
Materialize、提醒批次批准或提交的写操作继续采用明确 Dry Run/人工确认门禁，不自动扫描或发送通知。

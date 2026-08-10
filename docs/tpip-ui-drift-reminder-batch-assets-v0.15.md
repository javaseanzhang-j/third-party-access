# TPIP UI 漂移治理提醒批次运营资产 v0.15

## 1. 阶段目标

v0.15 将治理提醒批次、替代血缘、成员差异、Outbox/渠道投递关联和 Workspace 提醒指标接入 UI。页面只读取
既有批次与投递事实，不创建、批准、提交、取消或替代批次，也不触发通知发送。

## 2. 页面能力

新增 `/drift-reminder-batches`，以 Workspace 为强制范围：

- 展示批次总数及 DRAFT、APPROVED、DISPATCHED、CANCELLED 分布；
- 展示到期待入批执行、活动成员占用和最早到期时间；
- 展示 Outbox 路由状态、渠道投递状态与终态成功率；
- 展示批次编码、环境、来源、负责人、成员数、Outbox、替代血缘和 `rowVersion`；
- 详情抽屉展示 Content Checksum、Aggregation Key 和成员评估 Checksum；
- 未提交批次明确显示没有 Outbox 或渠道投递；
- 已提交批次展示安全 Outbox 概览和渠道状态，不展示地址、正文、Endpoint 或错误原文；
- 存在替代血缘时展示 ADDED、REMOVED、UNCHANGED、MODIFIED 成员差异；
- 支持返回治理评估，并从 Workspace 详情直接进入提醒批次资产。

## 3. 查询契约

```text
GET /control/v1/verification-drift-workbench/governance-reminder-batches?workspaceId={workspaceId}
GET /control/v1/verification-drift-workbench/governance-reminder-batches/{batchId}
GET /control/v1/verification-drift-workbench/governance-reminder-batches/{batchId}/diff
GET /control/v1/verification-drift-workbench/governance-reminder-batches/{batchId}/delivery-status
GET /control/v1/verification-drift-workbench/governance-reminder-metrics?workspaceId={workspaceId}
```

列表和 Workspace 指标随 Workspace 查询加载；详情、投递关联和替代差异按选择批次延迟读取，避免无选择时扫描
不需要的账本。

## 4. 生命周期边界

- `DRAFT` 是人工或默认关闭自动化生成的预览，不是审批或发送结果；
- `APPROVED` 只表示人工批准，仍未创建 Outbox；
- `DISPATCHED` 表示 Outbox 与执行预算已同事务提交，不等于渠道已经送达；
- `CANCELLED` 保留原批次证据，替代批次通过双向血缘关联；
- 终态成功率只使用 `DELIVERED + DEAD_LETTER`，没有终态样本时返回 0.00；
- 页面不提供任何写按钮，也不信任 `X-Operator` 作为认证身份。

## 5. 验收结果

```text
Test Files  21 passed (21)
Tests       38 passed (38)
vue-tsc / tsc / vite build passed
提醒批次资产页 chunk 11.17 kB（gzip 3.72 kB）
最大 JS chunk 433.43 kB（gzip 147.51 kB）
Playwright 静态发现 11 个场景
```

真实 MySQL 8.4 环境对 Workspace 23 的只读验收结果：

- 提醒批次 0；
- 到期待入批执行 0、活动占用 0；
- Outbox 路由和渠道投递均为 0；
- 终态投递成功率 0.00。

由于当前没有既有批次，本阶段没有制造提醒数据；详情、差异和投递查询由前端契约测试覆盖。Control Plane 已
优雅停止，18082 端口已释放；本阶段未启动浏览器。

## 6. 下一阶段建议

UI v0.16 先建设浏览器级可回滚验收夹具：生成隔离 Workspace、可行动超期报告、冻结执行账本、DRAFT/替代批次
和模拟投递状态，真实验证评估展开、指标、详情、差异和投递页面；验收结束后按明确 ID 回滚测试资产。完成该门禁
后，再评估是否将创建、批准、提交、取消和替代命令接入 UI。

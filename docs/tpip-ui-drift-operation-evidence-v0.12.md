# TPIP UI 治理操作证据与审计回查 v0.12

## 1. 阶段目标

v0.12 在漂移治理工作台 v0.11 的执行闭环之上补齐操作证据回查，使 Dry Run、资格拒绝和正式应用都能通过
`commandKey` 重建治理意图、操作者、请求完整性与逐项结果。本阶段复用既有不可变命令账本，不新增数据库迁移，
不改变治理状态机。

## 2. 页面能力

新增 `/drift-operations/:commandKey?`：

- 支持直接输入 Command Key 查询既有证据；
- 展示命令类型、Workspace、操作者、状态、Dry Run 属性和创建时间；
- 展示 SHA-256 请求校验和、幂等重放标识和请求快照；
- 展示目标数、应用数、拒绝数和逐项资格；
- 对账 Expected、Previous、Resulting Row Version；
- 展示稳定拒绝原因、目标状态和接受操作产生的后继基线；
- 支持回到漂移工作台或追溯 Workspace 资产。

Workspace 资产详情新增“进入漂移治理”入口，自动携带 `workspaceId`。漂移工作台在 Dry Run 后展示预检
Command Key 和证据入口；正式执行成功后自动进入该次操作的证据页。

## 3. 查询契约

```text
GET /control/v1/verification-drift-workbench/governance-operations/{commandKey}
```

页面只读取服务端保存的请求和结果快照，不读取 DriftReport 原始证据、Fixture、Provider 报文或 Secret。
未找到或非法 Command Key 不进行猜测或降级查询，由统一异步错误面板提示并允许重试。

## 4. 一致性约束

- Dry Run 和正式执行使用不同 Command Key，各自形成独立证据；
- Dry Run 后理由、负责人或选择集变化会立即作废旧预检结果；
- 正式执行仍携带当前 `reportId + rowVersion`，不因预检通过而跳过服务端校验；
- 命令成功后刷新报告与摘要，再按服务端返回的 Command Key 回查；
- UI 不修改账本证据，不提供删除、覆盖或人工伪造审计记录的能力；
- `X-Operator: local-operator` 仍只是本地单用户审计标签，不作为可信身份。

## 5. 验收结果

```text
Test Files  18 passed (18)
Tests       32 passed (32)
vue-tsc / tsc / vite build passed
治理操作证据页 chunk 6.21 kB（gzip 2.58 kB）
漂移治理工作台 chunk 9.99 kB（gzip 3.93 kB）
最大 JS chunk 427.27 kB（gzip 145.71 kB）
Playwright 静态发现 8 个场景
```

真实 MySQL 8.4 环境只读回查既有命令 `v04-rejected-202608091138` 成功：

- Workspace 23，操作类型 `ACKNOWLEDGE`；
- 状态 `REJECTED`，目标 1、应用 0、拒绝 1；
- 逐项原因 `REPORT_NOT_FOUND`；
- 请求 checksum、操作者、请求快照、目标状态和完成时间完整返回。

验收没有创建或执行新的治理命令。Control Plane 已优雅停止，18082 端口已释放；本阶段未启动浏览器。

## 6. 下一阶段建议

UI v0.13 建设治理运营度量：复用既有 `governance-metrics` 与 `groups` 查询，展示 Workspace SLA、负责人负载、
处置效率、命令质量、日趋势和漂移聚合簇，并保持指标来自服务端全量事实而非当前分页推断。

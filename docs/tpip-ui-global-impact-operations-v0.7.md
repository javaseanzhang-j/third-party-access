# TPIP UI 全局影响任务运营增强 v0.7

## 1. 目标

增强任务详情的人工运营闭环，让操作人能够判断任务为何未推进、快速定位失败 Workspace、识别证据临期风险，
并在 READY 状态下依据服务端事实完成封板前检查。

## 2. 能力

### 2.1 运行恢复引导

根据服务端 `recoveryRecommendation` 展示对应引导：

- `START_OR_ENABLE_WORKER`：检查独立 Worker 是否启用及自动化配置；
- `WAIT_FOR_ACTIVE_WORKER`：存在有效租约，等待 Worker 推进；
- `REDISPATCH_AFTER_LEASE_EXPIRY`：检查 Worker 日志，等待租约恢复；
- `REVIEW_FAILURE_AND_RETRY`：筛选失败项，修复原因后显式重试；
- `READY_FOR_MANUAL_SEAL`：进入人工封板检查；
- `NO_ACTION_TERMINAL`：终态只保留查询与审计操作。

UI 不调用内部 Worker API，也不持有或模拟 `X-Worker-Id` 和自动化 Token。

### 2.2 临期提示

只对 PENDING、RUNNING、FAILED、READY 状态提示：

- 剩余 15 分钟以内：Warning；
- 剩余 5 分钟以内：Critical；
- 已到期：阻断性说明；
- SEALED、EXPIRED、CANCELLED 不显示运行临期提示。

页面每 30 秒更新一次剩余窗口判断。

### 2.3 Workspace 筛选与诊断

筛选条件全部下推服务端稳定查询模型：

- 执行状态；
- Workspace 风险等级；
- Workspace 编码、名称或环境关键字；
- 服务端分页。

失败项展示安全错误分类、归一化失败摘要和尝试次数，不展示堆栈、SQL、Provider 报文或 Secret。

### 2.4 READY 封板前检查

封板面板同时检查：

1. 任务状态为 READY；
2. 全部 Workspace 成功；
3. 失败数量为 0；
4. 证据未过期；
5. 服务端 `allowedActions.SEAL` 为 enabled。

只有五项全部通过才允许打开既有封板确认弹窗。最终命令仍携带 Row Version，并接受 HTTP 409 冲突保护。

## 3. 工程实现

- `jobOperations.ts`：临期、恢复引导与封板检查纯模型；
- `SealPreflightPanel.vue`：READY 检查摘要；
- `GlobalImpactJobDetailPage.vue`：筛选、诊断、引导和临期交互；
- `globalImpactApi.ts`：Workspace 影响服务端过滤参数。

## 4. 验收结果

```text
Test Files  13 passed (13)
Tests       24 passed (24)
vue-tsc     passed
vite build  passed
任务详情异步 chunk 22.24 kB（gzip 8.20 kB）
最大 JS chunk 427.24 kB（gzip 145.70 kB）
```

本阶段未启动浏览器、Control Plane 或 Worker，未产生数据库变更。

## 5. 下一阶段建议

UI v0.8 建设全局运营态势页：停滞任务列表、SLO 分级、恢复建议聚合、优先级视图和从态势页进入任务处置。


# TPIP UI 全局影响运营态势 v0.8

## 1. 目标

新增全局运营态势页，将分散在单个任务详情中的停滞信息提升为统一工作台，使本地操作人能够先识别最紧急任务，
再进入任务详情完成诊断和受控处置。

## 2. 页面能力

路由：

```text
/global-impact-operations
```

页面提供：

- 停滞、Critical、Warning 和 Critical 阈值概览；
- 严重度、优先级、恢复建议组合过滤；
- 任务进度、停滞时长、最后推进时间和候选策略展示；
- 恢复建议分布；
- 从运营态势直接进入任务详情；
- 每 30 秒自动刷新和手工刷新；
- 查询达到 100 条上限时的明确截断提示。

所有停滞判定、SLO 分级和恢复建议均来自 v1.8 服务端 View API。UI 只做展示和当前结果集过滤，不依赖内部
`RuntimeState`，不调用 Worker 内部接口，也不在运营页直接执行写命令。

## 3. 工程实现

- `globalImpactOperationsApi.ts`：运营视图稳定契约与查询函数；
- `GlobalImpactOperationsPage.vue`：指标、过滤器、任务表和恢复建议聚合；
- 路由和应用导航新增“全局运营态势”；
- 响应式布局覆盖桌面和窄屏；
- API 单元测试锁定路径、参数和响应；
- Playwright 场景锁定从导航进入运营态势页的主路径。

## 4. 验收结果

```text
Test Files  14 passed (14)
Tests       25 passed (25)
vue-tsc     passed
vite build  passed
运营态势异步 chunk 6.39 kB（gzip 2.78 kB）
最大 JS chunk 427.24 kB（gzip 145.70 kB）
```

Playwright 共识别 4 个端到端场景；按照当前约束仅执行 `--list` 静态发现，不启动浏览器。
真实本地 View API 已连接 MySQL 完成只读验证，当前无停滞任务，未产生数据库变更。

## 5. 下一阶段建议

UI v0.9 建设策略资产浏览与版本详情，并打通任务候选策略到策略资产的只读导航，形成“运营异常 → 任务事实 →
策略定义”的完整追溯链路。


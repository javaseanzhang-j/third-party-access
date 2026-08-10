# TPIP UI 治理策略资产追溯 v0.9

## 1. 目标

建设治理策略资产浏览与版本详情，形成“运营态势/影响任务 → 候选策略版本 → 策略资产 → 关联任务/快照”的
只读追溯链路。

## 2. 页面能力

新增路由：

```text
/governance-policies
/governance-policies/{policyId}
/governance-policies/{policyId}/versions/{versionId}
```

### 2.1 策略资产列表

- 按关键字、Global/Workspace 作用域和 DRAFT/PAUSED/ACTIVE 状态筛选；
- 服务端分页；
- 展示 Workspace 身份、当前版本、版本数、影响任务数和最近更新时间；
- 从当前版本或策略资产进入详情。

### 2.2 策略详情

- 展示资产身份、作用域、当前版本和 Row Version；
- 展示完整不可变版本列表；
- 展示最近 20 条关联影响任务及执行进度；
- 可进入版本详情或既有任务详情。

### 2.3 版本详情

- 展示逾期阈值、聚合窗口、提醒间隔、负责人和最大提醒次数；
- 展示抑制漂移类型、抑制检查项与 Content Checksum；
- 标记当前选择版本；
- 展示使用该版本的最近影响任务。

### 2.4 跨页面追溯

任务列表、运营态势、任务详情和封板快照详情中的候选策略均可直接进入对应策略版本。策略版本中的影响任务又可
返回任务详情，保持双向只读导航。

## 3. 工程实现

- 新增 `governance-policy` Feature 边界；
- `governancePolicyAssetApi.ts` 封装 v1.9 稳定 View API；
- 三个按路由懒加载的页面；
- `PolicyImpactJobsTable.vue` 复用任务使用关系展示；
- 应用导航新增“治理策略资产”；
- API 单元测试与 Playwright 主路径场景。

页面不调用策略写命令，不提供发布、激活或暂停按钮，符合当前本地资产追溯阶段的只读边界。

## 4. 验收结果

```text
Test Files  15 passed (15)
Tests       27 passed (27)
vue-tsc     passed
tsc         passed
vite build  passed
策略列表异步 chunk 4.58 kB（gzip 2.08 kB）
策略详情异步 chunk 5.66 kB（gzip 2.23 kB）
版本详情异步 chunk 5.12 kB（gzip 2.13 kB）
最大 JS chunk 427.26 kB（gzip 145.71 kB）
```

Playwright 共识别 5 个端到端场景；按照当前约束仅执行 `--list`，未启动浏览器。真实本地 View API 已完成策略、
版本和关联 SEALED 任务只读验证。Control Plane 已优雅停止，18082、18084、18100 均无监听进程。

## 5. 下一阶段建议

UI v0.10 建设 Workspace 资产浏览与有效策略解析：展示 Workspace 基本身份、风险等级、当前解析到的 Global/
Workspace 策略、验证基线和最近漂移状态，并从 Workspace 反向进入策略版本与影响证据。


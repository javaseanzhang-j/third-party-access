# TPIP UI Workspace 资产追溯 v0.10

## 1. 页面能力

新增 `/workspaces` 和 `/workspaces/{workspaceId}`：

- Workspace 列表支持关键字、环境、生命周期、风险筛选和服务端分页；
- 展示有效策略来源、最新基线、基线数量、待处置漂移和变更项；
- 详情展示身份与风险、有效策略解析、不可变基线谱系、最近漂移报告和全局影响证据；
- 可从 Workspace 进入策略版本、影响任务；封板快照中的 Workspace 可反向进入资产详情；
- 当前无 ACTIVE 策略时明确展示系统默认，不误用 PAUSED/DRAFT 策略。

## 2. 验收结果

```text
Test Files  16 passed (16)
Tests       29 passed (29)
vue-tsc / tsc / vite build passed
Workspace 列表 chunk 5.07 kB（gzip 2.25 kB）
Workspace 详情 chunk 7.23 kB（gzip 2.60 kB）
最大 JS chunk 427.26 kB（gzip 145.71 kB）
```

Playwright 静态发现 6 个场景，未启动浏览器。真实 View API 已完成只读验收，未产生数据库变更；验收后
18082、18084、18100 均释放。

## 3. 下一阶段建议

UI v0.11 建设漂移工作台查询页：从 Workspace 详情进入可操作漂移清单，复用既有负责人分派、确认、批量 Dry Run
与处置能力，完成资产追溯到运营闭环的 UI 接续。

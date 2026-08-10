# TPIP UI 封板快照 v0.5

## 1. 本次交付

- 新增路由 `/global-impact-snapshots/:snapshotId`。
- 新增封板快照详情页，展示有效期、候选策略、checksum、消费证据和 Workspace 影响分页。
- 任务详情的“查看封板快照”操作改为真实跳转。
- 优先级调整、取消、失败重试和封板成功后，自动切换至审计时间线并标记“本次操作”。
- 命令弹窗增加组件测试，覆盖必填审计原因和原因标准化提交。

## 2. 前端边界

- UI 不解析 `impactDocument`，只使用 v1.7 稳定 View API。
- 继续保持本地无登录模式，不添加伪 JWT 或临时账号体系。
- 快照页为只读资产页，不提供重算、编辑或删除入口。
- 未执行浏览器视觉验收；本阶段以单元测试、严格类型检查和生产构建为验收依据。

## 3. 验收结果

```text
Test Files  10 passed (10)
Tests       17 passed (17)
vue-tsc     passed
vite build  passed
最大 JS chunk 391.91 kB（gzip 133.86 kB）
```

## 4. 后续

真实 HTTP 闭环已于 `20260809155841` 完成：10 个 Workspace 全部成功，任务从 READY 封板为 SEALED，
稳定快照 View API、分页、跳转能力和审计事件均通过。证据见
`e2e/global-impact-seal/evidence/20260809155841`。

后续：

1. 已补充参数化 Playwright 封板任务跳转场景；需要显式提供本地 Job/Snapshot ID 后执行浏览器验收。
2. 数据规模扩大时，将快照分页身份补齐改为专用 JOIN 查询端口。
3. 继续推进 UI v0.6 的治理任务创建/筛选体验或资产导航，具体范围按后续阶段确定。

# TPIP 治理工作台 UI 工程治理 v0.2

## 目标

在不改变 v1.4 服务端查询契约、不加入登录和写操作的前提下，解决 UI v0.1 的整库加载问题，并建立一致、可测试的异步查询状态。

## 已落地

- 使用 `unplugin-vue-components` 和 `ElementPlusResolver` 按需解析实际使用的 Element Plus 组件、指令和样式；
- 移除 `app.use(ElementPlus)` 与完整 `element-plus/dist/index.css`；
- 将 Element Plus、Vue Runtime、TanStack Query 和路由页面拆分为独立 chunk；
- 新增 `AsyncStatePanel`，统一加载骨架、空数据、失败提示和显式重试；
- 列表、任务详情、Workspace 影响和审计时间线均接入统一异步状态；
- 生成 `src/components.d.ts`，保持 Vue 模板严格类型检查；
- 新增组件状态测试，并保留 API Client 与展示映射测试。

## 构建基线

2026-08-09 production build：

| 制品 | 原始大小 | gzip |
| --- | ---: | ---: |
| 业务入口 JS | 4.17 KB | 2.04 KB |
| Element Plus JS | 380.00 KB | 129.92 KB |
| Element Plus CSS | 126.72 KB | 17.18 KB |
| Vue Runtime JS | 25.67 KB | 10.25 KB |
| TanStack Query JS | 35.70 KB | 10.55 KB |
| 列表路由 JS | 5.84 KB | 2.59 KB |
| 详情路由 JS | 7.22 KB | 2.85 KB |

最大 JS chunk 低于 Vite 500 KB 告警线。该结果来自真实按需引用和明确分包，不通过提高告警阈值规避问题。

## 质量门禁

- `CI=true pnpm test`：3 个测试文件、7 个测试全部通过；
- `CI=true pnpm build`：`vue-tsc`、Node 配置类型检查和 Vite production build 全部通过；
- Node 配置启用 `skipLibCheck` 仅跳过构建插件可选 bundler 的第三方声明，应用源码仍由严格模式检查；
- Vitest 内联 Element Plus，使按需 CSS 在 jsdom 测试链路中由 Vite 正常处理。

## 保持不变的边界

- 只监听 `127.0.0.1:18100`；
- 无登录、OIDC/JWT、RBAC；
- 不存储 Token、数据库口令或第三方 Secret；
- UI 写按钮仍只展示服务端 `allowedActions`，不执行命令；
- Playwright 仅保留验收骨架，本阶段未执行真实浏览器测试。

## 后续顺序

1. 任务详情影响摘要可视化与风险聚合；
2. Control Plane 健康状态提示；
3. 受控写操作 API 适配、确认对话框、幂等与并发冲突反馈；
4. 用户明确授权后执行 Playwright 真实浏览器验收。

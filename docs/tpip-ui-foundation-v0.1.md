# TPIP 治理工作台 UI 基础 v0.1

## 范围

UI 阶段已于 2026-08-09 正式开始。v0.1 建立 Vue 3 本地单用户工作台，并打通 v1.4 全局影响任务只读查询模型。

已完成：

- Vue 3、TypeScript、Vite、Element Plus 工程；
- Vue Router、TanStack Vue Query、Pinia 初始化；
- 本地无登录应用外壳和 loopback 开发服务器；
- 全局影响任务列表、关键字/状态/优先级筛选、分页和进度；
- 任务详情、任务事实、恢复建议与 `allowedActions`；
- Workspace 结构化影响分页；
- 审计时间线；
- 可插拔但当前为空的请求增强器；
- Vitest 单元测试与 Playwright E2E 骨架。

## 运行端口

- UI：`127.0.0.1:18100`；
- Control Plane：`127.0.0.1:18082`；
- Vite 仅代理 `/control` 和 `/actuator`，不配置外网监听。

## 工程边界

- 首期无登录、OIDC/JWT、RBAC、用户和角色管理；
- UI 只消费 v1.4 View DTO，不直接依赖数据库字段和命令聚合；
- Vue Query 管理服务端状态，Pinia 不复制任务数据；
- 当前按钮只显示服务端操作能力，不执行写命令；
- 不在浏览器存储 Token、数据库密码或第三方 Secret。

## 质量基线

- `vue-tsc --noEmit` 严格类型检查；
- Vitest 单元测试；
- Playwright 浏览器验收用例独立于单元测试；
- Vite production build；
- pnpm lockfile 和精确依赖版本；
- 只允许 `vue-demi` 官方安装脚本。

v0.1 的 Element Plus 完整组件库接入和约 1 MB 主包是首版基线。该遗留项已在 v0.2 通过按需组件解析和 vendor 分包解决，详见 `tpip-ui-engineering-v0.2.md`。

## 下一步

1. 建设任务详情的影响摘要可视化；
2. 接入 reprioritize、cancel、retry、seal 受控命令与确认对话框；
3. 增加 Control Plane 健康状态提示和 Playwright 真实浏览器验收。

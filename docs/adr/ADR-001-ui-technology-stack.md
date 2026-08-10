# ADR-001：TPIP 本地治理工作台 UI 技术栈

- 状态：ACCEPTED
- 批准日期：2026-08-09
- 适用范围：`tpip-ui`

## 决策

采用 Vue 3、TypeScript、Vite、Element Plus、Vue Router、TanStack Vue Query、Pinia、Vitest、Vue Test Utils 和 Playwright 建设 TPIP UI。

服务端状态由 Vue Query 管理；Pinia 仅管理主题、布局等设备本地界面状态；筛选和分页优先写入 URL Query。HTTP 使用基于原生 `fetch` 的轻量 Client，不额外引入 Axios。首期不引入 ECharts，出现真实图表需求时按需追加。

## 原因

TPIP 是表格、筛选、详情、状态、时间线和受控操作密集的企业治理工作台。Vue 3 与 Element Plus 能以较低复杂度覆盖这类交互，TypeScript 与后端稳定 View DTO 配合可以在编译期发现契约偏差。

## 本地认证边界

首期无登录、OIDC/JWT 和 RBAC。开发服务器只监听 `127.0.0.1`，通过 Vite 本地代理访问 Control Plane。API Client 预留请求增强器，但当前为空实现；浏览器不得保存 Token、数据库密码或第三方 Secret。

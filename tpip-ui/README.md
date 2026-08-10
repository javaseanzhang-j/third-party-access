# TPIP UI

TPIP 本地单用户第三方接入配置与运营治理工作台。技术栈为 Vue 3、TypeScript、Vite、Element Plus、Vue Router、TanStack Vue Query 和 Pinia。

默认首页是第三方接入配置中心。v0.22 已支持 Domain、Capability、Operation、Canonical Contract、不可变 Schema
Version，以及 Provider、CredentialRef、ProviderContract 稳定身份和 Endpoint Revision 的配置，并支持 Canonical
Version 与 Endpoint 发布；既有 Workspace、漂移和 Global Impact 页面位于
“运营治理”二级菜单。

v0.22 第三阶段新增 ProviderContractVersion、Binding 和 JSONPath Mapping。Mapping UI 根据方向和 Binding 自动
生成 Schema Reference，只允许选择已发布且归属正确的 Contract Version，并直接调用 Mapping Engine 执行样例测试。

v0.22 第四阶段新增受控 Policy DSL 和 BindingVersion。PolicyVersion 创建时由服务端立即编译，可查看真实执行计划；
BindingVersion 自动筛选并冻结当前 Binding 的全部已发布依赖，可在正式发布前预览完整 Bundle Manifest。

v0.22 第五阶段新增 FixtureSuite 验证资产。可按 Binding 创建 Suite、编辑多 Case 的 MAPPING/REMOTE_CALL 场景和
Fixture Assertion Profile 断言、生成不可变 FixtureSuiteVersion、查看内容与 checksum，并显式发布供 Workspace 验证使用。

v0.22 第六阶段新增 Workspace 配置与服务端验证。支持创建 DRAFT、装配同环境已发布 BindingVersion、选择已发布
FixtureSuiteVersion、执行真实 Verification Engine、查看结构化 Check 证据，并对失败任务显式重试。

v0.22 第七阶段新增评审与 Bundle 发布工作台。支持 PASSED 验证证据门禁、Submit Review、按风险执行
RELEASE/SECURITY 审批、查看审批历史、编译 READY Bundle、查看 Manifest，并显式发布为 PUBLISHED。

v0.22 第八阶段新增 Deployment 与 Runtime 调用工作台。支持从 PUBLISHED Bundle 创建 Deployment、真实预热、
首次全量或后续灰度激活、单调扩量、受控回滚、ACTIVE Route 和完整证据查看；业务系统调试只提交 Canonical Request，
并分别展示 HTTP 状态与标准业务结果。

v0.22 第九阶段新增可恢复接入向导。向导以现有服务端资产为完成依据，聚合八阶段进度、定位第一个缺口并跳转专家页面；
浏览器仅保存非敏感资产 ID 和环境用于断点恢复，刷新后会重新读取服务端事实。

Element Plus 采用组件与样式按需解析，生产构建将 UI 库、Vue Runtime、TanStack Query 和业务路由拆分为独立 chunk。列表、详情、Workspace 影响和审计时间线共享统一的加载、空数据、失败重试状态。

## 本地运行

先启动 Control Plane（默认联调端口 18082），再进入本目录执行：

```bash
pnpm install
pnpm dev
```

浏览器访问 `http://127.0.0.1:18100`。开发服务器仅监听 loopback，将 `/control`、`/actuator`、`/runtime-config`
代理到 Control Plane 18082，并将精确的 `/integration/` API 前缀代理到 Runtime 18081。

## 验证

```bash
CI=true pnpm test
CI=true pnpm build
```

提醒批次完整自动化门禁从工程根目录执行：

```bash
TPIP_MYSQL_PASSWORD='<local-password>' e2e/ui-governance-workbench/verify.sh
```

门禁会输出 v0.20 JSON/Markdown 归档报告；设置 `TPIP_E2E_ARTIFACT_DIR` 可将每次运行保存到稳定的唯一 Run ID
目录。报告同时记录审计链 checksum、失败阶段与清理复核结论。

门禁自行构建 Control Plane、启动隔离服务、运行单线程 Playwright 场景并回滚数据库数据。本地默认使用系统 Chrome；
设置 `TPIP_E2E_BROWSER_CHANNEL=bundled` 可改用已经安装的 Playwright Chromium。

首期无登录、OIDC/JWT 和 RBAC。API Client 的请求增强器当前为空实现，不得在浏览器存储 Automation Token、数据库密码或第三方 Secret。
CredentialRef 页面只接受 `env://TPIP_SECRET_*` 引用，不提供 Secret Value 输入框。

任务详情已支持受控调整优先级、取消、失败重试和封板。写请求携带 `rowVersion` 和本地审计 Operator，命令冲突返回 409 后只刷新、不自动重放。可通过 `VITE_TPIP_OPERATOR` 覆盖默认的非敏感审计标识 `local-ui`。

提醒批次详情提供独立的不可变命令审计时间线，展示创建、批准、取消、替代与提交的操作者、版本、原因和
Outbox 关联。时间线只读取 `tpip_audit_event`，不会依据批次当前状态反向生成历史事件。

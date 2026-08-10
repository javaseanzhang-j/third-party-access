# TPIP 第三方接入配置工作台 v0.22

> 状态：v0.22 第九阶段已落地  
> 适用范围：本地单机、单用户架构验证环境

## 1. 版本目标

v0.22 将 TPIP 的产品主线重新对准“第三方接入配置”。平台首页不再以漂移治理为中心，而是以一次第三方接口从建模、
适配、验证到发布的配置过程为中心。治理能力保留为二级菜单，用于配置资产投入运行后的运营闭环。

核心原则：

- 业务系统只依赖稳定的 Operation 和 Canonical Contract；
- Provider URL、Secret、第三方字段名、认证和容错差异由 TPIP 吸收；
- 稳定身份与不可变版本分离，变化通过新 Version/Revision 表达；
- 配置必须经过验证、评审和发布，不能直接修改运行态；
- Secret 只登记引用，永不在浏览器和配置数据库中保存或回显明文。

## 2. 产品信息架构

```text
集成配置（平台主入口）
├── 接入配置首页
├── 可恢复接入向导
├── 第三方系统
│   ├── Provider
│   ├── CredentialRef
│   ├── ProviderContract
│   └── Endpoint Revision
├── 业务标准接口
├── ProviderContractVersion / JSONPath Mapping
├── Policy DSL
├── BindingVersion
├── FixtureSuite
├── Workspace 配置与服务端验证
├── Workspace 评审、批准与 Bundle 发布
├── Deployment 预热、激活、灰度与回滚
└── Runtime Canonical Request 调用

运营治理（二级入口）
├── Workspace / Global Impact
├── 漂移治理
├── 治理策略和度量
└── 提醒批次与审计证据
```

默认路由为 `/integration-assets`；Deployment 和 Runtime 调用入口分别为
`/integration-assets/deployments`、`/integration-assets/runtime-invoke`。

## 3. 本阶段已经实现

### 3.1 接入配置首页

- 显示 Provider、CredentialRef、ProviderContract、Endpoint 的真实资产数量；
- 展示标准接入路径和各阶段建设状态；
- Control Plane 不可用时显示明确错误；
- 提供进入第三方系统配置的主操作。

### 3.2 Provider

- 查询第三方稳定身份；
- 创建供应商、渠道或外部平台；
- 配置稳定编码、名称、类型、负责人和说明；
- Provider 不包含环境、URL 和接口版本。

### 3.3 CredentialRef

- 按 Provider 查询和创建 CredentialRef；
- 当前仅允许 `env://TPIP_SECRET_*` 引用；
- UI 不提供 Secret Value 输入框，不发送或回显真实 Secret；
- 环境变量必须在使用该 Secret 的 Java 进程启动前注入。

### 3.4 ProviderContract

- 查询和创建第三方协议的稳定身份；
- 支持 HTTP、SOAP、GraphQL 和 gRPC 类型；
- 请求、响应和错误 Schema 属于不可变 ProviderContractVersion，未在本阶段 UI 开放。

### 3.5 Endpoint Revision

- 查询各 Contract 最新的环境化 Endpoint Revision；
- 创建地址、Method、资源路径、超时和 CredentialRef 绑定；
- 新 Revision 初始为 DRAFT；
- 支持显式连通性探测，DRAFT 可显式发布；
- 变更地址时创建新 Revision，不覆盖旧 Revision。

### 3.6 业务标准接口与 Canonical Schema

- Domain、Capability、Operation 分层查询和创建；
- Operation 配置调用模式、幂等分类、数据分级和负责人；
- Canonical Contract 按 REQUEST、RESPONSE、ERROR、EVENT 建立稳定身份；
- 创建 JSON Schema 2020-12 不可变版本和 Example；
- 服务端规范化 Schema、生成 checksum，DRAFT 经显式命令发布；
- 发布后通过新语义版本演进，不覆盖旧版本。

### 3.7 第三方协议版本与 JSONPath Mapping

- ProviderContractVersion 配置 Request、Response、Error、Callback Schema 和 Examples；
- ProviderContractVersion 由 DRAFT 显式发布，并保留规范化内容 checksum；
- Binding 连接一个 Canonical Operation 和一个 ProviderContract；
- Mapping 稳定身份支持 OUTBOUND_REQUEST 和 INBOUND_RESPONSE 核心方向；
- MappingVersion 只展示并引用属于当前 Binding 的已发布 Contract Version；
- UI 根据方向自动生成 `canonical-contract-version:*` 与 `provider-contract-version:*` 引用；
- JSONPath 规则支持顺序、Source/Target Selector、目标类型、必填、缺失策略和错误策略；
- MappingVersion 可以在发布前执行真实 Mapping Engine 样例测试，展示输出、诊断和编译计划 checksum；
- MappingVersion 通过显式命令发布，旧规则版本保持不可变。

### 3.8 Policy DSL 与 BindingVersion

- 展示 ACTIVE Policy Type Registry、允许挂载阶段、安全分级和运行时兼容范围；
- 当前注册能力为 `builtin.transport.inject@1.0.0` 和 `builtin.auth.api-key@1.0.0`；
- Policy 稳定身份从属于 Binding；
- Policy DSL 使用 `tpip.policy/v1alpha1`，不支持任意 Groovy 或实现类调用；
- 创建 PolicyVersion 时立即使用服务端编译器校验，失败不生成版本；
- 可以读取 Compiled Policy Plan、阶段、参数和 plan checksum；
- Secret 只能通过 `env://TPIP_SECRET_*` 引用，编辑器不接收真实 Secret；
- BindingVersion 自动筛选当前 Binding 下全部已发布依赖；
- 冻结 Canonical Request/Response、ProviderContractVersion、Endpoint、双向 MappingVersion 和可选 PolicyVersion；
- 服务端再次校验归属、方向、Schema Reference、编译和生命周期；
- BindingVersion 生成不可变 content checksum，DRAFT 经显式命令发布；
- 已发布 BindingVersion 可以预览完整 Bundle Manifest，包括 Schema、Mapping Plan、Policy Plan、Endpoint 和 Secret Reference。

当前后端没有单独的 Policy Fixture API。Policy 的配置期验证由创建时编译和 Plan 预览完成，执行期测试将在
FixtureSuite / Workspace Verification 阶段覆盖，不在 UI 中伪造“Policy 测试通过”。

### 3.9 FixtureSuite 验证资产

- FixtureSuite 作为 Binding 下的稳定测试资产，支持查询和创建；
- FixtureSuiteVersion 由一个或多个 FixtureCase 组成，创建时规范化内容并计算 SHA-256；
- Case 支持 `OUTBOUND_REQUEST`、`INBOUND_RESPONSE` 两个 Mapping 方向；
- 默认使用 `MAPPING` 执行模式，也可显式配置受安全门禁约束的 `REMOTE_CALL`；
- 编辑器支持 Source、Expected、期望成功状态、兼容诊断码和 FAP Assertions；
- FAP 支持 SUCCESS、DIAGNOSTIC_CODE、JSON_PATH、JSON_SCHEMA、POLICY_EXPRESSION，以及仅限远程调用的 HTTP 断言；
- UI 在提交前检查 JSON Object、非空 Assertions Array、Case 编码和重复编码；
- 服务端继续执行方向、模式、断言类型、JSONPath、Schema 和 Policy Expression 的权威校验；
- DRAFT 版本可显式发布，已发布版本内容和 checksum 不可修改；
- 已发布 FixtureSuiteVersion 将作为下一阶段 Workspace 服务端验证的输入。

### 3.10 Workspace 配置与服务端验证

- 新增独立的接入配置 Workspace 页面，与运营治理的 Workspace 追溯视图分离；
- 创建 DRAFT Workspace，配置环境、风险等级和负责人；
- Workspace v0.1 精确装配一个已发布 BindingVersion；
- UI 按 BindingVersion 的 Endpoint 环境过滤，服务端继续执行环境一致性权威校验；
- 只允许选择属于装配 Binding 的 ACTIVE FixtureSuite 和已发布 FixtureSuiteVersion；
- Verification Engine 在服务端执行 Binding 闭包、Fixture 归属、Bundle Preview、Endpoint Probe 和逐 Case 验证；
- 展示 FULL/REGRESSION Verification Job、通过/失败计数、证据 URI 和每项 Verification Check；
- Check 可以展开查看结构化 Details 与 Evidence，不使用前端模拟结果；
- 全部 Check 通过后，Workspace 从 DRAFT 自动转换为 VERIFIED；
- 验证失败保留 DRAFT，可以按原 FixtureSuiteVersion 显式重试；
- 页面基于 Row Version 执行命令，并识别 VERIFIED、IN_REVIEW、APPROVED、COMPILED 均已有通过验证。

### 3.11 Workspace 评审、批准与 Bundle 发布

- 发布工作台从 Workspace 最近一次带真实 Check 的 PASSED 验证读取证据；
- VERIFIED Workspace 可以显式 Submit Review，进入 IN_REVIEW；
- LOW/MEDIUM 风险需要 RELEASE 阶段批准；
- HIGH/CRITICAL 额外需要 SECURITY 阶段批准，并要求两个不同审批人；
- 审批人不能等于 Workspace Owner，UI 提前校验，服务端再次权威校验；
- 本地审批人代码作为该次 `X-Operator` 审计标签，不被描述为认证身份；
- 审批 Evidence Snapshot 默认引用 Verification Run、FixtureSuiteVersion、Evidence URI 和通过/失败计数；
- Approval History 展示阶段、审批人、决定、意见、证据引用和时间；
- REJECTED 会让 Workspace 回到 DRAFT，必须重新验证后再提交；
- APPROVED Workspace 可以编译 Bundle，冻结 Manifest 并生成 artifact checksum；
- 编译成功产生 READY Bundle，同时 Workspace 进入 COMPILED；
- READY Bundle 通过独立命令发布为 PUBLISHED，页面可查看完整 Manifest；
- Manifest 展示 Canonical/Provider Schema、Mapping Plan、Policy Plan、Endpoint Snapshot、Secret Reference 和兼容范围；
- 当前签名元数据仍为 `UNSIGNED`，页面明确展示，不伪装成已签名制品。

### 3.12 Deployment 与 Runtime 调用

- 从 COMPILED Workspace 的 PUBLISHED Bundle 创建 PENDING Deployment，环境直接继承 Bundle；
- 预热真实调用 Runtime，检查 Bundle checksum、Schema Profile、Endpoint、Policy Provider 和 Secret Reference；
- 达到实例法定人数后进入 READY，首次激活必须 100%，已有活动版本时允许从 10% 开始确定性灰度；
- 扩量只允许单调增加，所有命令携带真实 Row Version，禁止用前端猜测并发版本；
- 回滚不是把旧记录改回 ACTIVE，而是以历史 Bundle 创建新的 100% ACTIVE Deployment，保留完整血缘；
- 页面展示当前 ACTIVE Route、revision、流量目标、Deployment 历史及结构化预热/回滚证据；
- Runtime 调用页只要求业务系统选择稳定 `operationCode` 并提交 Canonical Request；
- Canonical Request 支持 requestId、caller、tenantId、idempotencyKey、deadline、字符串 attributes 和 payload；
- UI 区分 HTTP 状态与 `result.success/result.code`，不把 HTTP 200 等同于业务成功；
- 未发现 ACTIVE Route 时禁用调用，避免发送必然失败的请求。

### 3.13 可恢复接入向导

- 以 `Operation + ProviderContract + Environment + Binding + Workspace` 建立一次接入上下文；
- 实时聚合现有资产 API，不新增旁路数据库表，也不复制专家工作台中的配置内容；
- 八个阶段分别判断 Canonical、Provider、Mapping、BindingVersion、FixtureSuite、Verification、Bundle 和 ACTIVE Route；
- 每个完成状态必须有服务端已发布资产或生命周期事实，不允许用户手工勾选“已完成”；
- 自动定位第一个缺口，后续阶段显示为等待前置，并跳转到对应专家工作台处理；
- 浏览器断点仅保存非敏感资产 ID、环境和更新时间，不保存 Secret、Schema、Mapping、Policy 或业务报文；
- 刷新或重新进入页面后恢复上下文，并重新读取服务端事实，避免把旧的浏览器进度当作权威状态；
- “重置向导”只删除本地上下文，不删除或修改任何平台资产。

## 4. 配置流程

本阶段可以完成第三方侧基础信息：

```text
创建 Provider
  → 登记 CredentialRef
  → 创建 ProviderContract 稳定身份
  → 创建 Endpoint Revision
  → Endpoint Probe
  → Endpoint Publish
```

完整接入流程仍然是：

```text
Domain / Capability / Operation / Canonical Contract
  → Provider / CredentialRef / ProviderContractVersion / Endpoint
  → Request Mapping / Response Mapping / Policy DSL
  → BindingVersion
  → FixtureSuite / Workspace Verify / Review / Approve
  → Bundle Publish / Deployment Activate
  → Runtime Invoke
```

从 v0.23 第三阶段开始，普通配置人员可以在“服务管理 → 接入服务”中直接完成第一行的产品化创建：填写 `serviceCode`、服务名称及业务标准请求/返回，平台会自动建立内部 Operation、两份 CanonicalContract 和已发布的 `1.0.0` 版本。随后在服务详情中点击“添加第三方实现”，选择已配置的提供方接口。Domain、Capability、CanonicalContract 和 Binding 仍是权威技术资产，但不再是普通配置流程的前置知识。

## 5. API 对应关系

| UI 能力 | Control Plane API |
| --- | --- |
| 接入服务聚合查询/创建 | `GET/POST /control/v1/product-model/services` |
| 接入服务详情 | `GET /control/v1/product-model/services/{id}` |
| 添加第三方实现 | `POST /control/v1/product-model/services/{id}/targets` |
| 路由策略与版本查询 | `GET /control/v1/product-model/services/{id}/route-policy` |
| 保存新路由草稿 | `PUT /control/v1/product-model/services/{id}/route-policy` |
| 发布路由版本 | `POST /control/v1/product-model/services/{id}/route-policy/versions/{versionId}:publish` |
| 路由 Dry Run | `POST /control/v1/product-model/services/{id}/route-policy:dry-run` |
| 路由决策审计 | `GET /control/v1/product-model/route-decisions` |
| Provider 列表/创建 | `GET/POST /control/v1/providers` |
| CredentialRef 列表/创建 | `GET/POST /control/v1/credentials` |
| ProviderContract 列表/创建 | `GET/POST /control/v1/provider-contracts` |
| Endpoint 列表/创建 | `GET/POST /control/v1/endpoints` |
| Endpoint 发布 | `POST /control/v1/endpoints/{id}:publish` |
| Endpoint 探测 | `POST /control/v1/endpoints/{id}:probe` |
| ProviderContractVersion 查询/创建 | `GET/POST /control/v1/provider-contracts/{id}/versions` |
| ProviderContractVersion 发布 | `POST /control/v1/provider-contracts/{id}/versions/{versionId}:publish` |
| Binding 查询/创建 | `GET/POST /control/v1/bindings` |
| Mapping 查询/创建 | `GET/POST /control/v1/mappings` |
| MappingVersion 查询/创建 | `GET/POST /control/v1/mappings/{id}/versions` |
| Mapping 样例测试 | `POST /control/v1/mappings/{id}/versions/{versionId}:test` |
| MappingVersion 发布 | `POST /control/v1/mappings/{id}/versions/{versionId}:publish` |
| Policy Type Registry | `GET /control/v1/policy-types?activeOnly=true` |
| Policy 查询/创建 | `GET/POST /control/v1/policies` |
| PolicyVersion 查询/创建 | `GET/POST /control/v1/policies/{id}/versions` |
| Policy 编译计划 | `GET /control/v1/policies/{id}/versions/{versionId}/plan` |
| PolicyVersion 发布 | `POST /control/v1/policies/{id}/versions/{versionId}:publish` |
| BindingVersion 查询/创建 | `GET/POST /control/v1/bindings/{id}/versions` |
| BindingVersion 发布 | `POST /control/v1/bindings/{id}/versions/{versionId}:publish` |
| Bundle Manifest 预览 | `GET /control/v1/bindings/{id}/versions/{versionId}/bundle-preview` |
| FixtureSuite 查询/创建 | `GET/POST /control/v1/fixture-suites` |
| FixtureSuiteVersion 查询/创建 | `GET/POST /control/v1/fixture-suites/{id}/versions` |
| FixtureSuiteVersion 内容 | `GET /control/v1/fixture-suites/{id}/versions/{versionId}` |
| FixtureSuiteVersion 发布 | `POST /control/v1/fixture-suites/{id}/versions/{versionId}:publish` |
| Workspace 查询/创建 | `GET/POST /control/v1/workspaces` |
| Workspace 装配 BindingVersion | `POST /control/v1/workspaces/{id}/assets/binding-version` |
| Workspace 资产 | `GET /control/v1/workspaces/{id}/assets` |
| 执行 Workspace 验证 | `POST /control/v1/workspaces/{id}:verify` |
| Verification Jobs | `GET /control/v1/workspaces/{id}/verification-jobs` |
| Verification Checks | `GET /control/v1/verification-jobs/{runId}/checks` |
| Verification 失败重试 | `POST /control/v1/verification-jobs/{runId}:retry` |
| 提交 Workspace 评审 | `POST /control/v1/workspaces/{id}:submit-review` |
| Approval 查询/决定 | `GET/POST /control/v1/workspaces/{id}/approvals` |
| Bundle 查询/编译 | `GET/POST /control/v1/workspaces/{id}/bundles` |
| Bundle 发布 | `POST /control/v1/bundles/{id}:publish` |
| Deployment 查询/创建 | `GET/POST /control/v1/deployments` |
| Deployment 预热 | `POST /control/v1/deployments/{id}:preheat` |
| Deployment 激活 | `POST /control/v1/deployments/{id}:activate` |
| Deployment 扩量 | `POST /control/v1/deployments/{id}:traffic` |
| Deployment 回滚 | `POST /control/v1/deployments/{id}:rollback` |
| ACTIVE Route | `GET /runtime-config/v1/routes/{operationCode}/environments/{environmentCode}` |
| Runtime 标准调用 | `POST /integration/v1/operations/{operationCode}:invoke` |

本地写请求使用 `X-Operator: local-ui` 形成审计标签；它不是认证身份，当前 UI 只能绑定 loopback 使用。

## 6. 后续实施顺序

v0.22 后续按真实接入依赖继续，不再优先扩张治理功能：

1. 扩展 Policy Provider：按需增加 OAuth2、签名、重试等受控类型和运行时实现；
2. 制品签名：替换当前 `UNSIGNED` 本地原型元数据；
3. 向导增强：按实际使用反馈再增加上下文参数透传和跨专家页面返回定位，不提前复制表单。

## 7. 验收结论

- Vitest：32 个测试文件、63 个测试通过；
- TypeScript/Vite 生产构建通过；
- 本地浏览器验证 Policy Registry、DSL 编辑器、真实编译计划、BindingVersion 依赖筛选、Bundle Manifest 预览，
  以及 FixtureSuite 的真实 Binding 级联、已发布三场景版本、FAP 内容查看和多 Case 编辑器；
- Workspace 页面只读验收加载真实 COMPILED Workspace、BindingVersion #18、FixtureSuiteVersion #7，
  三次 PASSED FULL/REGRESSION Job 及每次 7 项服务端 Check；
- 发布工作台只读验收加载真实 RELEASE Approval、PUBLISHED Bundle、artifact checksum 和完整 Manifest；
- Deployment 页面只读验收加载真实 ACTIVE Route、三代 Deployment 历史、100% 流量以及实例法定人数、
  预热检查和回滚血缘证据；
- Runtime 调用页只读验收加载稳定 Operation、Canonical Request 编辑器与当前 ACTIVE Route，未发起第三方调用；
- 接入向导只读验收自动恢复 Customer Lookup 的 Operation、Provider Contract、Binding 和 COMPILED Workspace，
  八阶段服务端证据进度为 100%；刷新页面后上下文与进度保持一致；
- 生命周期门禁验收确认 COMPILED Workspace 的 Submit Review、Approval、Compile 命令全部禁用，且不存在 READY 发布按钮；
- 未在浏览器验收中提交写命令，未产生额外数据库测试数据；
- Credential API 单测明确验证请求中不存在 `secretValue`。

第九阶段把分散的专家工作台串成了可恢复接入旅程，同时继续以服务端资产和生命周期作为唯一完成依据。
至此 v0.22 已覆盖“向导导航 + 专家配置 + 验证评审 + 发布激活 + Runtime 调用”的本地完整产品闭环。

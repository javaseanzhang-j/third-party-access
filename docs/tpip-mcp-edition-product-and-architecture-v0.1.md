# TPIP MCP Edition 产品与架构规划 v0.1

## 1. 文档目的

本文定义 TPIP MCP Edition 的产品定位、核心模型、系统边界、工程结构和分阶段实施范围，作为后续产品设计、数据库设计、前端交互和工程交付的共同依据。

TPIP MCP Edition 不是重新建设一套第三方接入系统，也不是把第三方厂商接口直接暴露给 AI。它是在 TPIP 已有第三方接入、业务标准服务、Mapping、Policy、路由、Bundle、Runtime、服务授权和审计能力之上增加 MCP 协议入口。

## 2. 产品定位

TPIP Platform 形成两种对外接入形态：

```text
TPIP Platform
├── TPIP HTTP Edition
│   └── 面向传统业务系统、渠道系统和后端服务
└── TPIP MCP Edition
    └── 面向 AI Agent、智能助手和 MCP Client
```

统一产品定位：

> TPIP MCP Edition 将企业已经接入的第三方 API、SDK 和 OpenAPI 能力，转换为可描述、可授权、可验证、可发布、可路由和可审计的 MCP Tools。

## 3. 核心原则

1. MCP 是新增协议适配层，不替代 Runtime Pipeline。
2. MCP Tool 面向业务能力，不面向阿里云、腾讯云、华为云等具体厂商。
3. `serviceCode` 仍是 TPIP 内部稳定业务服务标识。
4. MCP Tool 必须显式配置和发布，禁止自动暴露全部接入服务。
5. MCP Tool 输入输出来自业务标准契约，不直接使用第三方原始报文。
6. MCP 调用必须复用调用方身份和服务授权，不能绕过 `ConsumerServiceGrant`。
7. Tool 是否可见不代表可以执行，`tools/call` 必须再次授权。
8. MCP 协议类型不得进入 TPIP 领域模块和 Runtime Core。
9. Runtime 继续只执行已发布 Bundle 和已发布授权快照。
10. Secret 只以 Secret Reference 流转，不进入 Tool Schema、Tool 描述、日志和调用结果。
11. 已发布 MCP Tool 版本不可原地修改，变更必须创建新版本。
12. 高风险和有外部副作用的 Tool 必须具备平台侧治理规则，不能只依赖客户端提示。

## 4. 总体架构

```text
MCP Client / AI Agent
        │
        │ Streamable HTTP：tools/list、tools/call
        ▼
TPIP MCP Server
        │
        ├── MCP协议解析与版本兼容
        ├── MCP客户端身份映射
        ├── Tool目录过滤
        ├── Tool输入输出适配
        └── MCP调用审计
        │
        ▼
TPIP Consumer Authorization
        │
        ├── ConsumerApplication
        ├── ConsumerCredential
        └── ConsumerServiceGrant
        │
        ▼
TPIP Runtime Pipeline
        │
        ├── Canonical Contract校验
        ├── Mapping
        ├── Policy
        ├── 路由、权重与故障切换
        └── Secret解析和第三方签名
        │
        ▼
第三方系统 / 渠道 / OpenAPI
```

## 5. 产品模型

### 5.1 MCP Server Profile

表示一个可以被 MCP Client 连接的 TPIP MCP 服务实例配置。

建议属性：

| 属性 | 说明 |
| --- | --- |
| `serverCode` | MCP Server稳定编码 |
| `serverName` | 面向人的中文名称 |
| `description` | Server整体能力说明 |
| `protocolVersion` | 支持的MCP协议版本 |
| `transport` | 第一阶段固定`STREAMABLE_HTTP` |
| `status` | 草稿、已发布、已停用 |
| `instructions` | 提供给AI理解Server用途的总体说明 |

第一阶段平台只需要一个默认 Server Profile，但模型允许未来按业务域或安全边界拆分多个 MCP Server。

### 5.2 MCP Tool Asset

MCP Tool Asset 表示某项 TPIP 业务标准服务是否允许通过 MCP 暴露。

```text
Canonical Operation
  └── MCP Tool Asset
        └── MCP Tool Version
```

Tool Asset 建议属性：

| 属性 | 说明 |
| --- | --- |
| `toolCode` | TPIP内部资产编码 |
| `toolName` | MCP协议使用的稳定工具名称 |
| `operationId` | 关联Canonical Operation |
| `serviceCode` | 冗余查询字段，不作为新的事实来源 |
| `ownerCode` | 负责人 |
| `status` | 启用、停用 |

### 5.3 MCP Tool Version

版本内容包括：

| 属性 | 说明 |
| --- | --- |
| `versionNo` | 平台自动递增版本 |
| `title` | 面向人的中文工具名称 |
| `description` | 供AI判断调用时机的业务描述 |
| `fixedScenario` | 可选的固定授权场景，避免AI任意选择敏感场景 |
| `inputSchema` | MCP Tool输入JSON Schema |
| `outputSchema` | MCP Tool输出JSON Schema |
| `readOnlyHint` | 是否只读 |
| `destructiveHint` | 是否可能造成破坏性影响 |
| `idempotentHint` | 是否幂等 |
| `openWorldHint` | 是否与外部世界交互 |
| `confirmationMode` | 不需要、建议确认、强制确认 |
| `timeoutMs` | Tool执行超时 |
| `lifecycleStatus` | 草稿、已验证、已发布、已废弃 |
| `contentChecksum` | 不可变内容校验值 |

`title` 和 `description` 必须使用业务语言。例如：

```yaml
serviceCode: sms.send
toolName: sms_send
title: 发送业务短信
description: 向指定手机号码发送验证码或业务通知短信；不用于营销群发
readOnlyHint: false
destructiveHint: false
idempotentHint: false
openWorldHint: true
confirmationMode: REQUIRED
```

## 6. TPIP资产与MCP能力映射

| TPIP资产 | MCP表达 |
| --- | --- |
| Canonical Operation | Tool对应的业务能力 |
| `operationCode/serviceCode` | Tool到Runtime的稳定路由键 |
| Canonical Request Contract | `inputSchema`来源 |
| Canonical Response Contract | `outputSchema`来源 |
| Operation中文名称 | Tool `title`初始建议值 |
| Operation业务说明 | Tool `description`初始建议值 |
| ConsumerApplication | MCP客户端或Agent应用身份 |
| ConsumerServiceGrant | Tool可见和可调用权限 |
| Integration Binding | Tool后端可执行实现 |
| Route Policy | 第三方厂商选择规则 |
| FixtureSuite | Tool发布前验证用例 |
| Bundle | Runtime执行的不可变实现 |

Tool Schema 可以由 Canonical Contract 生成初稿，但必须保存为独立 Tool Version 快照。Canonical Contract 后续升级不能静默改变已发布 Tool。

## 7. MCP调用流程

### 7.1 Tool发现

```text
MCP Client调用 tools/list
  → 识别MCP客户端应用
  → 加载已发布MCP Tool快照
  → 加载已发布服务授权快照
  → 仅保留当前应用已授权serviceCode
  → 返回Tool名称、说明和Schema
```

### 7.2 Tool执行

```text
MCP Client调用 tools/call
  → 校验Tool存在且已发布
  → 再次校验调用方服务授权
  → 校验场景、来源和有效期
  → 校验MCP arguments符合inputSchema
  → 转换为InvocationRequest
  → 调用Runtime Pipeline
  → 将InvocationResponse转换为structuredContent
  → 记录MCP与Runtime关联审计
```

MCP协议错误和业务执行错误必须区分：未知 Tool、无效 JSON-RPC 等协议问题使用 MCP 协议错误；第三方拒绝、业务校验失败等执行问题返回 Tool Result，并标记 `isError=true`。

## 8. 身份和授权

### 8.1 本地验证阶段

本地阶段暂不建设完整 OAuth Server。MCP Server 只绑定 `127.0.0.1`，通过受控配置将本地 MCP Client 映射为一个 `ConsumerApplication`，继续执行该应用的服务授权。

本地身份映射不能成为远程部署默认配置。

### 8.2 远程部署阶段

远程 Streamable HTTP 使用 MCP 标准兼容的 OAuth2/OIDC 认证，将 `client_id`、Token Subject 或可信网关身份映射到 `ConsumerApplication`。

现有 HMAC 入口继续服务传统 HTTP 调用方，但不要求通用 MCP Client 实现 TPIP 私有 HMAC 算法。

### 8.3 授权约束

- `tools/list` 按已发布授权过滤；
- `tools/call` 重新校验授权；
- 默认禁止调用方指定具体第三方厂商；
- Tool授权最终映射到 `serviceCode`；
- Tool版本更新不自动扩大调用方权限；
- 紧急撤销必须在授权快照最大陈旧窗口后安全拒绝。

## 9. 安全设计

1. 本地服务绑定 localhost，远程服务必须使用 TLS。
2. Streamable HTTP 必须验证 `Origin`，拒绝不可信来源。
3. Tool Schema 和描述中禁止出现 Secret、内部密钥名称和第三方凭据。
4. Tool参数、返回内容和审计日志按数据分级进行脱敏。
5. 短信发送、文件写入、订单操作等外部副作用 Tool 必须显式标记。
6. 平台侧执行强制确认、幂等、限流和配额；MCP annotations 只作为客户端提示。
7. Tool描述作为不可信配置治理，防止通过描述注入越权指令。
8. MCP Client传入的 `_meta` 不得直接成为路由、授权或Secret选择依据。
9. Tool执行前后记录同一 `requestId`，贯通 MCP、Runtime、路由和第三方调用审计。
10. MCP Tool不得访问设计态Mapping和Policy，只调用已发布Runtime能力。

## 10. 工程结构

### 10.1 v0.1最小结构

```text
third-party-access
├── tpip-mcp-server-app
│   ├── domain
│   │   ├── McpToolDefinition
│   │   └── McpToolCatalog
│   ├── application
│   │   ├── McpToolCatalogService
│   │   ├── McpToolInvocationService
│   │   └── McpClientIdentityResolver
│   ├── adapter
│   │   ├── mcp
│   │   ├── runtime
│   │   └── authorization
│   └── configuration
├── tpip-runtime-core
├── tpip-control-plane-app
└── tpip-ui
```

第一阶段只增加 `tpip-mcp-server-app`，在模型稳定前不拆分更多 Maven 模块。

### 10.2 稳定后的结构

当 Tool 契约被多个应用复用后，再提取：

```text
tpip-mcp-contract
tpip-mcp-core
tpip-mcp-server-app
```

领域和应用端口使用 TPIP 自有类型；官方 MCP Java SDK 只允许出现在 `adapter.mcp` 和启动配置中。

## 11. 技术选型

- Java 21；
- Spring Boot 3.5.x；
- 官方 MCP Java SDK；
- 第一阶段采用 SDK 2.0.x 支持的 MCP `2025-11-25` 和 Streamable HTTP；
- MCP SDK通过BOM统一管理版本；
- TPIP自有模型继续使用Jackson 2；
- SDK适配层负责MCP JSON模型转换；
- 不采用已经废弃的HTTP+SSE作为新实现主路径；
- 后续通过协议适配层支持MCP `2026-07-28`，不修改领域和Runtime核心。

## 12. 前端信息架构

```text
MCP能力
├── MCP服务概览
├── MCP Tool管理
├── Tool发布管理
├── Agent与客户端
├── Tool服务授权
├── MCP调用测试
└── MCP调用审计
```

v0.1只建设“MCP Tool管理”和“MCP调用测试”的最小查询与操作流程。界面使用中文业务术语，并在技术字段旁提供解释。

## 13. 分阶段实施

### 13.1 TPIP MCP v0.1：工具调用闭环

- 新建 `tpip-mcp-server-app`；
- 提供 `/mcp` Streamable HTTP入口；
- 支持 `tools/list` 和 `tools/call`；
- 将已发布业务标准服务显式配置为 MCP Tool；
- 从 Canonical Contract 生成 Tool Schema初稿；
- MCP Tool映射到 `serviceCode`；
- 复用 Runtime Pipeline；
- 复用调用方服务授权；
- 本地调用方身份映射；
- MCP调用审计；
- 自动化测试和MCP一致性验收。

### 13.2 TPIP MCP v0.2：MCP资产管理

- Tool版本、验证、发布、下线；
- Tool中文说明编辑；
- Tool风险属性和确认策略；
- Tool与Canonical Contract变更影响分析；
- Tool配置前端工作台；
- 调用示例和客户端配置自动生成。

### 13.3 TPIP MCP v0.3：AI调用治理

- Agent和MCP Client正式身份管理；
- QPS、配额和并发限制；
- 高风险Tool强制确认；
- 数据分级、脱敏和审计查询；
- 长任务和异步结果；
- Tool调用成本、成功率和第三方路由分析。

### 13.4 TPIP MCP v1.0：企业版本

- OAuth2/OIDC和企业网关；
- mTLS；
- 多MCP Server Profile；
- Resources、Prompts和Tasks；
- 集群部署和协议版本兼容；
- 企业审批、合规和紧急阻断闭环。

## 14. v0.1验收标准

1. MCP Client能够连接 `/mcp`；
2. `tools/list` 只返回显式发布且当前应用已授权的 Tool；
3. Tool名称、中文说明、输入输出Schema完整；
4. `tools/call` 能够通过 Runtime Pipeline 调用已发布第三方实现；
5. 未授权 Tool、无效参数、过期授权和不可用 Bundle 均安全拒绝；
6. MCP调用不读取设计态Mapping、Policy或Secret；
7. MCP调用与Runtime调用审计可通过同一requestId关联；
8. 新增功能具有领域测试、适配器契约测试和端到端测试；
9. 通过MCP官方一致性或Inspector验证；
10. 不影响现有HTTP调用入口和前端配置流程。

## 15. 本阶段冻结项

以下内容不进入 v0.1：

- MCP Resources；
- MCP Prompts；
- MCP Tasks长任务；
- OAuth/OIDC完整环境；
- 多租户；
- 多MCP Server拆分；
- 基于AI自动生成和发布Tool；
- MCP Client指定第三方厂商；
- MCP SDK类型进入领域模块；
- 自动暴露全部Canonical Operation。

## 16. 版本与分支决策

- 产品版本线：`TPIP MCP Edition v0.1`；
- 开发分支：`codex/tpip-mcp-edition`；
- 基线分支：`feature/business-oriented-integration-redesign`；
- 首个工程目标：建立不依赖MCP SDK的Tool核心模型和应用端口，再接入官方MCP Java SDK；
- 每一阶段完成后更新本文实施状态和平台使用手册。

## 17. 当前实施状态

已完成第一阶段本地工具调用闭环：

- 父工程已加入 `tpip-mcp-server-app` Maven模块；
- 建立不依赖Spring MVC和MCP SDK的 `McpToolDefinition`、`McpToolAnnotations` 和 `McpClientIdentity`；
- Tool定义包含稳定名称、中文标题、业务说明、`serviceCode`、输入输出Schema、风险提示、确认方式、版本号和内容校验值；
- Tool Schema采用防御性复制，避免已发布定义被调用方原地修改；
- 建立 `McpToolCatalog` 端口，目录实现必须按调用方已发布服务授权返回Tool；
- 建立 `McpToolGatewayService`，将MCP Tool调用转换为标准 `InvocationRequest` 并通过现有 `RuntimePipeline` 执行；
- MCP调用元数据写入 `protocol=mcp`、Tool名称、Tool版本、调用方和业务场景，为后续贯通审计提供依据；
- 未出现在当前调用方目录中的Tool在进入Runtime前安全拒绝；
- 引入官方 MCP Java SDK 2.0.0，通过 Streamable HTTP 暴露 `/mcp`；
- 支持 MCP `initialize`、`tools/list` 和 `tools/call`，输入由SDK根据Tool JSON Schema校验；
- MCP Server使用受控的本地固定应用身份，自行使用Secret Reference解析密钥并签名调用Runtime，通用MCP Client无需实现TPIP HMAC；
- `tools/list` 只返回“已配置为MCP Tool”且“当前应用已获得已发布服务授权”的交集；
- 通过 `fixedScenario` 可把Tool限制在固定业务场景，避免调用方自行选择敏感场景；
- 入口默认关闭并只监听 `127.0.0.1`，浏览器Origin采用允许名单校验；
- 已补充模型、授权目录、Control Plane授权快照、Runtime签名调用、协议转换、安全校验和官方MCP客户端端到端测试；
- 本地启用和验证方式见 [TPIP MCP Edition 本地使用指南 v0.1](./tpip-mcp-edition-local-usage-v0.1.md)。

第二阶段后端资产闭环已完成：Control Plane新增MCP Tool稳定主资产和不可变版本、V54数据库迁移、JSON Schema规范化、风险规则验证、发布证据、查询API和最新发布快照；MCP Server默认读取Control Plane快照，本地静态Tool配置仅能通过显式测试开关启用。配置流程见 [MCP Tool资产配置与发布指南 v0.2](./tpip-mcp-tool-asset-configuration-v0.2.md)。

第三阶段中文业务化工作台已完成：新增“服务管理 → AI 工具开放”，提供工具概览、搜索、版本历史和四步配置向导；标准请求与返回自动取自业务服务的已发布 Canonical Contract，并以中文字段表单呈现；支持保存草稿、平台验证、确认发布以及从已发布版本创建新版本。列表同时区分最新版本和最新已发布版本，避免把待发布草稿误认为线上生效版本。

当前仍有两项明确限制：Tool与授权在MCP Server启动时形成快照，发布或授权变更后需要重启MCP Server；当前仅支持本机固定身份，不支持远程OAuth/OIDC客户端。下一段实施内容是契约变更影响分析与本地 MCP Client 配置/联调辅助。

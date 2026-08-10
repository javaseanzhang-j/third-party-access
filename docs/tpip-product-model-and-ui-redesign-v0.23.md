# TPIP 产品模型与前端交互重构方案 v0.23

## 1. 决策与范围

本阶段冻结测试、生产环境物理隔离方案，不调整环境模型，不改动现有运行时的 Bundle 不可变和设计态隔离原则。

本阶段解决两个问题：

1. 将面向架构师的资产界面重构为面向第三方接入人员的任务式界面。
2. 在现有 Operation、Binding、Bundle 基础上定义 `serviceCode` 多目标路由模型。

现有 Provider、ProviderContract、Endpoint、CredentialRef、Mapping、Policy、Binding、BindingVersion、FixtureSuite、Workspace、Bundle 和 Deployment 继续作为技术底座。

## 2. 用户产品模型

### 2.1 提供方模型

```text
第三方系统
  └── 接入通道
        └── 第三方接口
```

- **第三方系统**：外部能力提供者，例如阿里云、腾讯云或本地会员中心。
- **接入通道**：当前平台连接第三方的一组 URL、账号、凭据、公共参数和公共规则。同一个第三方可以有多个 URL 不同的通道。
- **第三方接口**：通道下一个具体的 HTTP 接口，保存 Method、Path、原生请求和响应结构以及接口差异。

通道地址与接口地址的关系为：

```text
最终请求地址 = 接入通道 baseUrl + 第三方接口 resourcePath
```

### 2.2 服务模型

```text
接入服务（serviceCode）
  └── 服务适配目标
        └── 第三方接口
```

- **接入服务**：TPIP 对业务系统提供的稳定能力，`serviceCode` 不包含厂商身份。
- **服务适配目标**：将接入服务的标准请求、返回转换为某个第三方接口协议的适配关系。
- **服务路由策略**：从多个可用适配目标中按规则、优先级和权重选择一次调用目标。

例如 `sms.send` 可以同时包含阿里云、腾讯云和华为云三个服务适配目标。

### 2.3 对外调用模型

```http
POST /api/v1/invoke
X-TPIP-Service-Code: sms.send
X-TPIP-Request-Id: <业务幂等请求号>
```

`serviceCode` 标识稳定服务，而不是某一个第三方接口。运行路径为：

```text
serviceCode
→ 路由规则匹配
→ 可用目标过滤
→ 优先级/权重选择
→ 参数与规则合并
→ Mapping/Policy
→ 第三方调用
→ 标准返回转换
```

## 3. 参数与规则继承

### 3.1 参数解析顺序

```text
接入通道公共参数
  < 第三方接口专用参数
  < 服务适配目标转换结果
  < 本次调用动态参数
```

动态参数只能覆盖声明为调用方可传入的参数，不能覆盖 Secret、签名密钥和受保护系统参数。

参数必须声明：

- 显示名称和参数编码；
- 来源：固定值、Secret 引用、业务请求、系统时间、UUID、Mapping 或 Policy 输出；
- 位置：Path、Query、Header、Cookie、Body 或 Signature；
- 数据类型、必填性、敏感性和调用方是否允许覆盖。

### 3.2 规则解析顺序

```text
接入通道公共规则
  → 第三方接口专用规则
  → 服务适配目标专用规则
```

下级规则支持继承、新增、合并、覆盖、禁用和替代。控制台必须显示最终有效配置、每一项来源和差异；发布时将解析结果冻结进不可变 Bundle。

## 4. 路由模型

路由执行顺序固定为：

```text
条件匹配 → 状态/健康过滤 → 最小优先级组 → 组内按权重选择 → 决策审计
```

v0.23 路由契约至少包含：

- 固定目标；
- 目标启停；
- 优先级；
- 权重；
- 健康状态过滤；
- 手工摘除；
- Dry Run；
- 路由决策审计；
- 仅在明确未发送时进行故障切换。

权重只在同一优先级的可用目标内归一化。超时且结果未知时默认禁止跨厂商重试，避免短信、支付、下单等场景产生重复业务结果。

## 5. 现有资产映射

| 用户产品对象 | 现有技术资产 | 处理方式 |
|---|---|---|
| 第三方系统 | Provider | 直接复用 |
| 接入通道 | Provider + CredentialRef + Endpoint 公共部分 + Policy | 新增聚合边界 |
| 第三方接口 | ProviderContract + ProviderContractVersion + Endpoint Revision | 聚合展示 |
| 接入服务 | Operation + CanonicalContract | 以业务名称和 serviceCode 展示 |
| 服务适配目标 | Binding + BindingVersion | 直接复用并补充路由属性 |
| 请求/返回转换 | Mapping | 隐藏技术术语，使用样例驱动配置 |
| 参数组装和认证 | Policy | 使用表单和规则模板配置 |
| 可执行版本 | Bundle + Deployment | 继续保持不可变发布 |

现有 Binding 是 Operation 到 ProviderContract 的单个适配关系，不等同于多目标路由。多目标路由需要增加独立的服务路由领域模型。

## 6. 聚合 API 契约

产品 API 使用 `/control/v1/product-model` 前缀；底层资产 API 保持兼容。聚合命令必须在应用服务内完成一致性校验，不能让前端串联十余个资产请求模拟事务。

### 6.1 第三方系统

```text
GET  /control/v1/product-model/providers
POST /control/v1/product-model/providers
GET  /control/v1/product-model/providers/{providerId}
```

### 6.2 接入通道

```text
GET  /control/v1/product-model/channels?providerId=
POST /control/v1/product-model/channels
GET  /control/v1/product-model/channels/{channelId}
PUT  /control/v1/product-model/channels/{channelId}
GET  /control/v1/product-model/channels/{channelId}/effective-configuration
POST /control/v1/product-model/channels/{channelId}:probe
```

通道命令聚合 baseUrl、CredentialRef、公共参数和公共 Policy，不返回 Secret 原文。

### 6.3 第三方接口

```text
GET  /control/v1/product-model/interfaces?channelId=
POST /control/v1/product-model/interfaces
GET  /control/v1/product-model/interfaces/{interfaceId}
PUT  /control/v1/product-model/interfaces/{interfaceId}
GET  /control/v1/product-model/interfaces/{interfaceId}/effective-configuration
POST /control/v1/product-model/interfaces/{interfaceId}:test
```

### 6.4 接入服务与适配目标

```text
GET  /control/v1/product-model/services
POST /control/v1/product-model/services
GET  /control/v1/product-model/services/{serviceId}
POST /control/v1/product-model/services/{serviceId}/targets
PUT  /control/v1/product-model/services/{serviceId}/targets/{targetId}
POST /control/v1/product-model/services/{serviceId}:test
```

### 6.5 路由

```text
GET  /control/v1/product-model/services/{serviceId}/route-policy
PUT  /control/v1/product-model/services/{serviceId}/route-policy
POST /control/v1/product-model/services/{serviceId}/route-policy:dry-run
GET  /control/v1/product-model/route-decisions?serviceCode=&requestId=
```

`dry-run` 只计算候选过滤和路由结果，不执行第三方调用。

## 7. 前端信息架构

```text
接入配置
├── 接入总览
├── 第三方系统
├── 接入通道
└── 第三方接口

服务管理
├── 接入服务
├── 服务路由
└── 测试与发布

运行观察
├── 调用控制台
├── 调用记录
└── 路由记录

高级管理
├── 标准契约
├── Mapping / Policy
├── BindingVersion
├── FixtureSuite / Workspace
└── Bundle / Deployment
```

普通页面以中文名称为主，技术编码自动生成并收纳到高级设置；Domain、Capability、CanonicalContract、BindingVersion 等词不作为普通接入流程的必备知识。

## 8. 实施顺序

1. **产品壳重构**：导航、接入总览、通道/接口/服务聚合只读模型，高级资产收口。
2. **提供方配置中心**：实现通道公共参数、Secret 引用和接口覆盖的聚合命令。
3. **接入服务中心**：以 serviceCode 聚合 Operation、Contract、Binding 和 Mapping。
4. **路由引擎**：实现路由策略、目标、Dry Run、决策审计和运行时选择。
5. **测试发布闭环**：接口测试、目标测试、服务测试和 Bundle 冻结。
6. **文档与迁移**：以 `sms.send` 三厂商场景更新使用手册，旧资产页面移入高级管理。

## 9. 验收基线

以 `sms.send` 为首个贯穿场景：阿里云、腾讯云、华为云各自拥有不同通道 URL、凭据和接口适配，业务调用方只传递 `sms.send` 和标准请求；平台按照优先级、权重及健康状态选择目标，并记录完整路由证据，返回稳定响应结构。

## 10. 实施状态

### v0.23 第一阶段

- 前端导航已调整为接入配置、服务管理、高级管理和运营治理；
- 已提供接入总览、接入通道、第三方接口和接入服务聚合页面；
- Operation 以 serviceCode 展示，Binding 以服务适配目标展示；
- 原有技术资产页面保留在高级管理。

### v0.23 第二阶段

- V41 新增 `tpip_access_channel`、`tpip_access_channel_interface` 和 `tpip_access_parameter`；
- 接入通道拥有独立 baseUrl 和 CredentialRef；
- 通道可以显式关联同一 Provider 下的 ProviderContract；
- 参数支持 Path、Query、Header、Cookie、Body 和 Signature 位置；
- 参数支持固定值、Secret 引用、业务请求、系统时间、UUID、表达式、Mapping 和 Policy 输出；
- 接口参数可以覆盖或禁用通道同位置、同编码的公共参数；
- 有效配置接口返回最终参数以及 `CHANNEL/INTERFACE` 来源证据；
- 前端可创建通道、关联接口、配置参数并预览接口最终有效配置。

第二阶段没有将通道参数直接注入 Runtime。后续必须在 BindingVersion/Bundle 编译阶段将有效配置转换为受控 Mapping/Policy 执行计划，并继续遵守 Runtime 不读取设计态数据库的边界。

### v0.23 第三阶段

- 新增产品聚合 API：`/control/v1/product-model/services`；
- 普通用户一次提交服务名称、`serviceCode`、标准请求和标准返回，不再逐项创建 Domain、Capability、Operation 和 CanonicalContract；
- 平台自动维护内部目录 `tpip.integration / tpip.access-services`；
- 创建接入服务时，在同一事务中创建 Operation、REQUEST/RESPONSE CanonicalContract，并生成和发布不可变 `1.0.0` 契约版本；
- 服务详情聚合标准契约和全部适配目标，可将已有 ProviderContract 直接添加为第三方实现；
- 重复绑定同一个第三方接口会被拒绝；失效服务或失效第三方接口不能成为新目标；
- 前端字段改用“业务调用编码、业务标准请求、业务标准返回、第三方实现”等产品语言，技术资产仍可在高级管理中查看。

第三阶段没有实现目标优先级、权重、健康过滤和路由 Dry Run。下一阶段将在服务适配目标之上建设版本化路由规则，并把最终选择结果编译进 Bundle；Runtime 仍不读取控制面设计库。

### v0.23 第四阶段

- V42 新增服务路由策略、不可变策略版本、版本目标快照和路由决策审计表；
- 服务路由与 Deployment 灰度路由分离：前者选择第三方 Binding，后者选择同一服务的 Bundle 发布版本；
- 固定执行顺序为：条件匹配 → 启用/人工摘除 → 健康过滤 → 最小优先级组 → 组内权重选择；
- 目标支持启停、0～10000 优先级、1～10000 权重、`HEALTHY_ONLY/HEALTHY_OR_UNKNOWN` 健康要求和人工摘除；
- 条件首版使用调用属性 JSON 的键值精确匹配，例如 `{"tenant":"vip"}`；空对象表示匹配所有调用；
- 故障切换只能配置为禁止，或仅在明确确认“尚未发送”时执行；结果未知的超时不得跨厂商重试；
- 每次保存生成新的 DRAFT 快照，发布后不可原地修改；Dry Run 只允许使用已发布版本；
- Dry Run 返回每个候选目标的健康状态、是否合格及排除原因，并写入可按 serviceCode/requestId 查询的决策审计；
- 接入服务详情增加“配置路由”，可以配置目标、发布版本并模拟调用属性和目标健康状态。

第四阶段仍属于设计态和验证态能力。下一阶段需要在 Bundle 编译时冻结已发布路由版本、目标 BindingVersion 和有效通道配置，并让 Runtime 从 Bundle 执行服务路由；Runtime 不得直接查询 V42 表。

### v0.23 第五阶段

- Bundle Manifest 新增可选的 `serviceRoutePlan`。没有路由计划的历史 Bundle 继续按单 Binding 执行；含路由计划的 Bundle 要求 Runtime `>=0.2 <1.0`；
- 编译服务 Bundle 时解析当前已发布的路由版本，并为每个目标选择最新的已发布 BindingVersion；路由版本号、校验和、目标条件、优先级、权重、健康要求和人工摘流状态全部冻结；
- 每个目标同时冻结其 ProviderContract、双向 Mapping、Policy、Endpoint 和 Secret Reference，所有目标 Secret Reference 合并进入 Bundle 声明；
- 路由目标必须属于同一个 Operation、必须存在已发布 BindingVersion，并且 Endpoint 环境必须与 Bundle 环境一致，否则编译失败；
- Runtime 在标准请求校验后、请求 Mapping 前执行服务路由，固定顺序仍为条件匹配、启停/摘流、健康过滤、最小优先级、同优先级权重；
- 默认路由键依次取调用属性 `routingKey`、幂等键和 requestId。同一路由键在同一版本和同一健康事实下保持稳定；
- Runtime 只读取 Bundle 内的执行快照，不查询 `tpip_service_route_*`、Binding、Mapping、Policy 或 Endpoint 设计表；
- Runtime 增加实例内被动健康事实：新目标视为健康，连续三次传输失败后标记不健康，成功响应后恢复；多实例聚合健康将在后续接入共享健康源；
- Bundle 预热会逐个检查所有路由目标的 Provider Schema、Endpoint、Policy Provider 和 Secret 可用性；任一目标不完整都不能通过预热；
- Workspace 验证证据记录路由版本 ID、内容校验和和目标数量；若路由在验证后发生变化，Bundle 编译会拒绝并要求重新验证，避免“审核一版、发布另一版”；
- Runtime 审计增加 `selectedBindingVersion`、`routeVersionId` 和脱敏后的 `routingKeyHash`，可还原一次请求为何调用了某个第三方实现；
- Bundle compiler 升级为 `tpip-bundle-compiler/0.2.0`，Runtime 默认版本升级为 `0.2.0`。

第五阶段完成了“配置路由进入真实调用链路”的闭环。当前不执行跨厂商自动重试：即使策略为 `ONLY_NOT_SENT`，也只冻结该安全约束；在传输层尚不能提供可靠的“明确未发送”证据前，Runtime 不会冒险切换目标。

下一阶段处理通道公共参数执行计划：将 Channel/Interface 的最终有效参数编译为受控 IR，支持 Header、Query、Path、Body、Cookie 与签名输入，同时明确多个通道与 Binding/Endpoint 的选择关系。

### v0.23 第六阶段

- V43 在 `tpip_binding_version` 增加可选的 `access_channel_id`。一个不可变 BindingVersion 现在显式冻结“第三方接口版本 + Endpoint Revision + 接入通道”，不再依赖运行时猜测通道；历史版本保持 `NULL` 兼容；
- 创建和发布 BindingVersion 时校验通道处于启用状态、属于同一第三方、已经关联该第三方接口，并且通道 `baseUrl` 与 Endpoint Revision 的 `baseUrl` 一致，避免配置阶段看似可用、发布后才发现组合错误；
- Bundle 编译按“通道公共参数 → 接口参数覆盖/禁用”的优先级解析最终有效参数，并将结果冻结到目标 Endpoint 快照中的 `accessParameterPlan`；Runtime 仍然只读取 Bundle，不访问通道参数设计表；
- 执行计划支持 `FIXED`、`SECRET_REF`、`REQUEST`、`MAPPING_OUTPUT`、`POLICY_OUTPUT`、`SYSTEM_TIME` 和 `UUID` 数据源，以及 Header、Query、Path、Body、Cookie 和 Signature 位置；
- Runtime 固定在请求 Mapping 和 `AFTER_REQUEST_MAPPING` Policy 之后装配参数，再执行 Provider Request Schema 校验及 `BEFORE_TRANSPORT` Policy。这样业务请求先变成第三方请求，再由通道计划补齐公共认证参数和协议参数；
- Secret 只以引用进入 Bundle，预热阶段验证可用性，调用阶段临时解析；Secret Header、Cookie 和签名上下文会登记为敏感数据，并在传输完成或请求结束时清理；
- `EXPRESSION` 不由 Runtime 临时解释，必须迁移为经过编译和审计的 Policy DSL；`SECRET_REF` 当前只允许用于 Header、Cookie 或 Signature，防止凭据进入 URL、路径或业务 Body；
- Signature 当前提供的是签名输入装配能力，不代表已经实现 HMAC、RSA 或厂商专用签名算法。具体算法将在受控 Policy Provider 中实现，不能回退到任意 Groovy 脚本；
- 通道的 `CredentialRef` 用于声明通道凭据归属和预热依赖；真正需要发送的 `appKey`、`appSecret`、`accessKeyId` 等仍以接入参数逐项配置，才能明确名称、位置、来源和是否允许接口覆盖。

第六阶段完成了“通道/接口有效参数进入真实调用链”的基础闭环。下一阶段应把这些技术对象收敛到面向普通配置人员的“添加第三方实现”向导中，并提供常见认证/签名模板以及通道公共 Policy、接口专用 Policy 的继承与覆盖规则。

### v0.23 第七阶段

- “添加第三方实现”从只创建 Binding 改为产品化执行向导，普通配置人员不再需要依次进入 Mapping、Policy 和 BindingVersion 高级页面；
- 向导按第三方接口筛选已发布协议版本、已关联且启用的接入通道，以及与通道 `baseUrl` 一致的已发布 Endpoint；不允许组合不一致的技术资产；
- 新产品入口按“serviceCode + 第三方接口 + 接入通道”识别实现，同一个第三方接口可以通过 URL 不同的多个通道形成多个独立路由目标；完全相同的接口与通道组合会被拒绝；
- 字段映射使用逐行可读格式：`$.来源字段 -> $.目标字段 [类型] [required]`。请求方向表示“业务字段到第三方字段”，返回方向表示“第三方字段到业务字段”，提交时转换为受控 JSONPath Mapping IR；
- 单个产品命令在同一事务中创建 Binding、请求 Mapping 及已发布版本、返回 Mapping 及已发布版本、可选 Policy 及已发布版本、BindingVersion 并发布；任一步失败则整体回滚；
- 首版认证模板提供“使用通道公共参数”和“API Key Policy”。API Key 模板只选择 CredentialRef、Header 名称和值前缀，生成 `builtin.auth.api-key@1.0.0` Policy DSL，不保存 Secret 原文；
- 新增产品命令 `POST /control/v1/product-model/services/{id}/targets:provision`。原有 `POST .../targets` 继续保留给兼容调用和高级模式；
- 向导 Dialog 使用视口约束和内部滚动。1280×720 视口下正文区域可滚动，底部“校验、生成并发布实现”按钮始终可见；
- HMAC、RSA、OAuth2 等未注册受控 Provider 的算法不会显示为“已支持”，也不会通过任意脚本执行。

第七阶段完成了普通 HTTP/API Key 接口的“一次配置即可形成可执行版本”。下一阶段建设 Policy 组合模型：通道公共 Policy、接口覆盖 Policy、实现专用 Policy 按确定顺序合并，并新增 HMAC-SHA256 等经过测试和注册的标准 Provider。

### v0.23 第八阶段

- V44 注册 `builtin.auth.hmac-sha256@1.0.0`，只允许挂载在 `BEFORE_TRANSPORT`，安全级别为 `CRITICAL`；
- HMAC 模板只持有 Secret Reference，不接收或持久化密钥原文；运行时临时解析密钥，生成签名后清理密钥字符、字节和敏感 Header；
- 签名原文使用受控插值模板，可读取 `context`、`provider` 等已编译上下文；输出支持小写十六进制和 Base64，并支持受限 Header 名称与前缀；
- Bundle 预热现在识别 HMAC Provider，并校验其 Secret Reference 已由 Bundle 声明且运行环境可解析；
- “添加第三方实现”向导增加 HMAC-SHA256 认证选项，可配置凭据、签名 Header、签名原文、编码和前缀，提交后自动生成、编译并发布 Policy DSL；
- Policy Engine 新增确定性组合器。调用方按“通道公共 → 第三方接口 → 具体实现”提供已编译层；后层同名 step 覆盖前层，也可以显式禁用前层 step；组合结果产生稳定 checksum；
- 组合器只处理受控的 `CompiledPolicyPlan`，不会加载或执行 Groovy，也不会在 Runtime 查询设计态数据库。

第八阶段完成了标准 HMAC Provider 和组合算法底座。当前通道/接口 Policy 仍缺少独立的版本化挂载资产，Bundle 目前仍使用
BindingVersion 选择的实现级 Policy。下一阶段需要增加 Scope 为 `CHANNEL/INTERFACE/IMPLEMENTATION` 的不可变 Policy
挂载版本、产品化配置 UI，并在 Bundle 编译时调用组合器冻结最终有效计划。

# TPIP 业务视角第三方接入整改方案 v0.24

## 1. 目标

普通用户围绕“接入并发布一个第三方能力”完成配置，不直接装配 ProviderContract、Endpoint、Policy、BindingVersion 和 Bundle。
这些技术资产继续作为不可变执行底座，由产品层配置编译生成。

业务产品层级固定为：

```text
第三方系统 → 产品/服务 → 接入通道 → 第三方接口 → 第三方报文结构版本
                                         ↓
业务接入服务 serviceCode → 第三方实现 → 多目标路由 → 验证与发布
```

## 2. 核心边界

| 产品对象 | 负责内容 | 不负责内容 |
| --- | --- | --- |
| 第三方系统 | 厂商稳定身份 | 账号、URL、接口字段 |
| 产品/服务 | 厂商内部能力分组 | serviceCode 路由 |
| 接入通道 | Base URL、凭据组、公共参数、认证模板 | Method、Path、报文字段 |
| 第三方接口 | Method、Path、Content-Type、接口差异 | Base URL、厂商账号 |
| 第三方报文结构 | 请求、返回、错误、回调和样例的不可变版本 | URL、签名、路由 |
| 业务接入服务 | 标准报文和 serviceCode | 厂商原生报文 |
| 第三方实现 | 通道、接口、报文结构和 Mapping 的冻结组合 | 修改被引用资产 |

最终调用地址统一由 `AccessChannel.baseUrl + InterfaceTransportVersion.resourcePath` 计算。

## 3. 凭据与认证

一个认证凭据组由多个命名字段组成。公开字段保存受控配置值；敏感字段仅保存 `CredentialRef`，不保存 Secret 原文。

```text
阿里云短信账号 A
├── accessKeyId       PUBLIC_VALUE
└── accessKeySecret   SECRET_REF
```

认证模板声明所需凭据字段和配置 Schema，模板版本发布后不可修改。通道认证配置版本绑定：

```text
接入通道 + 认证模板版本 + 凭据组 + 非敏感参数 → 编译后的 Policy DSL
```

合并顺序保持为“通道公共 → 接口覆盖 → 具体实现”，后层可以用相同步骤编码覆盖前层，也可以显式禁用前层步骤。

## 4. 兼容策略

- `tpip_provider_contract_version` 继续承载第三方报文结构版本，不做职责迁移。
- `tpip_endpoint` 暂时保留，逐步变成由通道和接口调用信息生成的执行快照。
- `tpip_access_policy_version` 暂时保留，逐步变成认证模板配置编译后的执行产物。
- `tpip_binding_version`、Bundle 和 Runtime 保持旧字段可读；新产品 API 在过渡期双写或生成兼容资产。
- V46 中间关联表继续保留兼容查询；V51 增加直接产品外键作为新的权威归属。

## 5. 数据迁移

| 版本 | 内容 |
| --- | --- |
| V47 | 认证凭据组和字段 |
| V48 | 认证模板及不可变版本 |
| V49 | 通道认证配置版本 |
| V50 | 接口调用信息版本 |
| V51 | 通道、接口直接产品归属以及通道凭据组引用 |
| V52 | 内置 API Key、HMAC-SHA256 认证模板及其已发布初始版本 |

测试和生产继续物理隔离；`environment_code` 在旧表中仅作为兼容元数据，不作为跨环境路由机制。

## 6. UI 主流程

普通导航只保留：

1. 第三方接入；
2. 业务服务；
3. 验证与发布；
4. 运营治理。

第三方接入按“第三方 → 产品/服务”进入详情，在同一工作区完成通道、凭据组、认证模板、接口、报文结构和联调。
添加第三方实现时只选择通道、接口和已发布报文结构版本，不重复配置通道认证。

ProviderContract、Mapping、Policy DSL、BindingVersion、Workspace、Bundle 和 Deployment 移入高级管理。

## 7. 生效原则

设计态配置发布后不会直接改变 Runtime。任何变更都必须重新生成执行闭包，完成 Workspace 验证、Bundle 编译和 Deployment 激活。
Runtime 只执行 Bundle 中冻结的配置和 checksum，不读取设计态认证模板、凭据组或报文结构表。

## 8. 首批落地接口与兼容生成

业务配置接口统一位于 `/control/v1/business-integration`：

| 场景 | 接口 |
| --- | --- |
| 查询认证方式 | `GET /authentication-templates` |
| 配置并发布通道认证 | `POST /channels/{channelId}/authentication-versions` |
| 配置并发布接口调用信息 | `POST /third-party-interfaces/{interfaceId}/transport-versions` |
| 查看最终请求预览 | `GET /channels/{channelId}/interfaces/{interfaceId}/request-preview` |

请求预览默认选取最新已发布的通道认证版本和接口调用版本，展示最终 URL、Method、超时、认证 Header、凭据字段来源等信息。Secret 只显示引用名称，预览过程不解析 Secret 明文。

业务服务添加第三方实现时使用：

```text
POST /control/v1/product-model/services/{serviceId}/targets:provision-business
```

用户只选择接入通道、第三方接口、接口调用版本、第三方报文结构版本和字段映射。系统自动完成以下兼容动作：

1. 由通道 Base URL 和接口调用版本生成并发布旧 `Endpoint` 执行快照；
2. 创建并发布 Mapping、BindingVersion；
3. Bundle 编译时自动加入最新已发布的通道认证策略；
4. 按“通道认证 → 通道通用规则 → 接口规则 → 实现规则”合并 Policy；
5. 把版本号、checksum、Secret 引用和策略来源冻结到 Bundle，Runtime 不回读设计态表。

旧 `/targets:provision` 接口暂时保留，供既有数据和高级管理模式使用；新业务 UI 不再要求用户选择 Endpoint 或手工填写认证 Policy。

## 9. 业务接入工作区 UI

普通用户通过 `/integration-assets/provider-access` 进入“第三方接入工作区”，页面按照以下四步显示完成状态：

1. 第三方系统与产品服务；
2. 接入通道与 Base URL；
3. 账号凭据与通道认证；
4. 第三方接口与接口调用版本。

工作区支持：

- 创建第三方系统、产品服务和接入通道；
- 创建账号凭据组，公开标识保存配置值，敏感字段只选择 Secret 引用；
- 选择表单化认证方式，保存草稿并发布；
- 创建第三方接口时同时创建首个接口调用信息草稿；
- 接口调用版本号由平台自动生成，用户只维护 Method、Path、报文类型和超时；
- 组合已发布的通道认证和接口调用版本，查看脱敏的最终请求预览。
- 在认证配置中把账号凭据里的公开字段（例如 `appKey`、`AccessKeyId`）绑定到第三方要求的 Header、Query 或 Body 参数；
- 通过表单配置通道所有接口共用的请求参数，以及某个接口的覆盖或禁用项；
- 请求参数支持固定值、业务请求字段、Secret 引用、系统时间、UUID 和字段映射结果，普通配置不需要编写脚本；
- 当前通道的接口选择只显示已经绑定到该通道的接口，避免同一产品下其他通道的接口混入。
- 在所选接口下直接维护第三方请求、返回、错误和回调报文结构；版本号自动递增，发布版本不可原地修改；
- 普通报文通过业务字段表单配置中文含义、JSONPath、类型、示例、必填和说明，由平台生成 JSON Schema 与样例；复杂组合结构保留高级 JSON Schema 模式。

旧 Provider、CredentialRef、Endpoint 技术配置页面迁移至“高级管理 → 第三方技术资产”。通道参数、原始 Policy、Mapping、BindingVersion 等技术能力不删除，继续作为高级配置和兼容底座。

## 10. OpenAPI 账号字段与请求封装

平台将 OpenAPI 调用中的配置分为三层，避免把 `appKey`、签名密钥和业务参数混在一张无语义的键值表中：

1. **账号凭据**：表示第三方发放的一套账号。`appKey`、`AccessKeyId` 等可公开标识保存为公开字段；`appSecret`、`AccessKeySecret` 等敏感字段只保存 Secret 引用。
2. **认证方式**：说明这套账号如何参与认证。用户选择账号后，可以把公开字段绑定为第三方要求的参数名和位置；签名密钥由认证模板在运行时使用，页面和 Bundle 都不读取密钥明文。
3. **请求参数封装**：表示与账号认证无关或需要独立维护的通用 Header、Query、Body、Cookie、Path 参数。通道级参数对当前通道所有接口生效，接口级参数按“位置 + 参数名”覆盖或禁用公共配置。

例如阿里云短信账号的 `AccessKeyId=LTAI...` 可以在“账号与认证”中配置为 Query 参数 `AccessKeyId`；签名密钥选择一个 Secret 引用；短信接口固定要求的 `Format=JSON`、`Version=2017-05-25` 则在“公共参数与接口覆盖”中配置为通道公共 Query 参数。若某个接口使用不同版本，可新增接口专用的同名 `Version` 参数覆盖公共值。

保存认证草稿时，Control Plane 校验公开字段确实属于所选账号、不是敏感字段且目标位置合法。编译 Bundle 时，公开字段的实际配置值会作为非敏感固定参数冻结到 `accessParameterPlan`；Secret 仍只冻结引用。显式请求参数与账号字段绑定目标相同时，以显式请求参数为准，防止重复发送。

Secret 类型请求参数只能通过普通页面发送到 Header 或 Cookie。需要把密钥作为签名输入时，应使用认证模板或高级 Policy DSL，不允许普通用户把 Secret 放入 Query、Path 或 Body。

## 11. 第三方报文结构的业务配置

“报文结构”归属于第三方接口，而不是接入通道。一个接口即使被多个通道使用，也共享同一组可追溯的报文结构版本；具体业务服务添加第三方实现时，再选择并冻结一个已发布版本。

业务工作区增加第五个完成步骤和“报文结构”页签。选择接口后，可以查看每个版本的请求字段数、返回字段数、包含的报文类型和发布状态。普通用户采用字段表单录入：

| 配置项 | 含义 | 示例 |
| --- | --- | --- |
| 中文含义 | 供配置人员识别的业务名称 | 手机号码 |
| JSONPath | 字段在第三方报文中的真实路径 | `$.mobile`、`$.data.requestId` |
| 字段类型 | 文本、数字、是/否、对象、数组 | 文本 |
| 示例值 | 自动形成请求或返回样例 | `13800000000` |
| 必填 | 生成 JSON Schema 的 required 约束 | 是 |
| 字段说明 | 第三方字段用途 | 接收短信的手机号码 |

平台根据字段表单生成 JSON Schema 和请求/返回样例。嵌套对象可直接使用多段 JSONPath；数组元素、多态结构、`oneOf` 等复杂协议切换“高级 JSON Schema”维护。多个版本可以同时处于“已发布”状态，用于历史追溯和回滚，但一次可执行实现只冻结其中一个版本。

字段映射不放在第三方接口配置页：映射关系必须同时看到业务标准报文和第三方报文，因此在“业务服务 → 添加第三方实现”中配置。该流程现已改为左右字段联动选择、JSONPath 自动生成和双向样例即时预览。

## 12. 业务化字段映射与实现生成

添加第三方实现时，普通用户只选择第三方、产品、接口、已发布报文结构版本、接入通道和已发布接口调用版本。页面不再暴露旧 Endpoint，也不再要求重复选择 Secret 或配置认证 Policy：

- Endpoint 执行快照由平台根据“通道 Base URL + 接口调用版本 Method/Path/超时”自动生成并发布；
- 账号认证、公开账号字段、通道公共参数和接口覆盖继承所选通道的已发布配置；
- 业务标准请求/返回字段从 Canonical Contract Version 的 JSON Schema 提取；
- 第三方请求/返回字段从所选 Provider Contract Version 的 JSON Schema 提取。

映射工作台分为两个方向：

1. 请求转换：业务标准请求字段 → 第三方请求字段；
2. 返回转换：第三方返回字段 → 业务标准返回字段。

字段下拉同时显示中文名称、JSONPath 和必填状态。平台按路径末级字段名进行一次不重复的自动匹配，用户补充不一致字段，例如 `$.mobile → $.PhoneNumbers`。选择目标字段后自动带出目标类型和必填约束，仍可按实际协议调整。

样例预览默认读取业务标准契约的请求样例和第三方报文版本的返回样例；没有显式样例时，根据 Schema 的 `example`、`default` 和字段类型生成基础样例。预览同时执行请求与返回方向，检查路径存在性、必填字段和 STRING/NUMBER/BOOLEAN/OBJECT/ARRAY 类型转换。最终提交仍由服务端 Mapping Compiler 重新编译和校验，前端预览不能绕过服务端约束。

页面通过 `POST /control/v1/product-model/services/{serviceId}/targets:provision-business` 一次提交。Control Plane 在同一事务内生成 Endpoint 快照、双向 MappingVersion、BindingVersion 并发布；任一步失败均整体回滚。旧 `/targets:provision` 仅保留兼容使用。

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

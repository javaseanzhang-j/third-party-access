# TPIP 调用方接入与服务授权方案 v0.25

## 1. 背景

TPIP 平台提供的统一接入服务可能被多个渠道系统、多个业务项目和多个应用共同使用。业务系统通过稳定的 `serviceCode` 调用平台，例如 `sms.send`、`face.verify`、`oss.upload`，但知道 `serviceCode` 不代表拥有调用权限。

早期系统使用白名单限制调用方可以访问的接口。该思路可以保留，但需要从简单白名单升级为正式的“调用方身份与服务授权模型”：先识别谁在调用，再判断它是否可以调用指定服务，最后约束调用频率、数据范围和调用场景。

## 2. 设计目标

本方案需要满足：

1. 多个项目、渠道系统和应用可以分别接入 TPIP；
2. 每个调用方只能使用明确授权的 `serviceCode`；
3. 一个项目下不同应用使用独立身份和凭据，能够单独停用和轮换；
4. 支持授权有效期、来源网络、限流、配额和业务场景约束；
5. 调用方默认不能指定具体第三方厂商，第三方选择仍由 TPIP 路由治理；
6. Secret 不保存明文，Policy 不读取 Secret 原文；
7. Runtime 不查询设计态授权配置，只执行已发布的授权快照；
8. 已发布授权和凭据版本不可原地修改；
9. 保留未来接入 API 网关、OAuth2、OIDC/JWT 和 mTLS 的扩展空间。

## 3. 与其他认证授权能力的边界

平台存在三类不同的安全关系，不能混合建模：

| 能力 | 解决的问题 | 示例 |
| --- | --- | --- |
| 管理后台认证与 RBAC | 哪个人可以在管理后台执行什么操作 | 管理员可以发布，观察员只能查看 |
| 调用方身份与服务授权 | 哪个业务应用可以调用哪些 TPIP 服务 | 会员中心可以调用 `sms.send` |
| 第三方通道认证 | TPIP 如何向外部厂商证明身份 | 阿里云 `AccessKeyId` 和签名 |

本方案只负责第二类。即使本地管理后台暂不登录，业务系统调用 Runtime 时仍然可以启用调用方授权。

## 4. 核心产品模型

```text
调用方项目 ConsumerProject
  └── 调用方应用 ConsumerApplication
        ├── 调用凭据 ConsumerCredentialVersion
        ├── 网络访问规则 NetworkRule
        └── 服务授权 ConsumerServiceGrant
              └── 授权策略版本 ConsumerServiceGrantVersion
                    ├── serviceCode
                    ├── 有效期
                    ├── QPS 与突发限制
                    ├── 日配额
                    ├── 业务场景
                    └── 路由约束
```

### 4.1 调用方项目

表示一个业务项目、渠道系统或组织单元，例如：

- 会员中台；
- 营销平台；
- 门店渠道系统；
- 电商订单中心。

项目用于归属、负责人和统计，不直接作为运行时调用身份。

### 4.2 调用方应用

表示真正发起调用的部署单元。同一项目可以创建多个应用，例如：

- 会员中心生产服务；
- 会员中心定时任务；
- 会员中心数据同步服务。

不同应用必须使用独立凭据，避免一套密钥泄漏后只能停用整个项目。

### 4.3 服务授权

服务授权表达“调用方应用可以调用哪个业务服务”。例如：

```text
会员中心生产服务
  ├── 允许 sms.send
  └── 允许 face.verify

营销活动服务
  └── 允许 sms.send
      ├── QPS 50
      ├── 每日配额 100000
      └── 场景 marketing
```

旧系统的“白名单”迁移后就是最基础的服务授权，不再单独维护一张语义不足的白名单表。

## 5. 调用协议

第一阶段建议采用 `appKey + HMAC-SHA256`。调用示例：

```http
POST /runtime/v1/invoke
X-TPIP-App-Key: member-center-prod
X-TPIP-Timestamp: 1786348800000
X-TPIP-Nonce: 64d73f781aa74cfa
X-TPIP-Signature: 69200d...
X-TPIP-Service-Code: sms.send
X-TPIP-Scenario: login-verification
X-Request-Id: request-001
Content-Type: application/json
```

```json
{
  "mobile": "13800000000",
  "templateCode": "LOGIN_CODE",
  "parameters": {
    "code": "123456"
  }
}
```

`serviceCode` 只表示调用哪一种业务服务，不能作为认证凭证。建议签名原文至少覆盖：

```text
HTTP Method
请求路径
serviceCode
timestamp
nonce
请求体 SHA-256
```

签名密钥只通过 Secret Reference 解析，不写入数据库、日志、审计事件、Bundle 或错误响应。

## 6. Runtime 鉴权流程

```text
接收统一调用请求
  → 根据 appKey 识别调用方应用
  → 检查应用状态与凭据有效期
  → 校验 HMAC、timestamp 和 nonce
  → 检查来源网络规则
  → 根据 serviceCode 查找已发布服务授权
  → 检查授权状态和有效期
  → 检查场景、QPS 和每日配额
  → 校验业务标准报文
  → 执行 TPIP 第三方实现路由
  → 执行 Mapping 与 Policy Bundle
  → 调用第三方系统
  → 记录脱敏调用审计
```

鉴权失败时必须在进入 Mapping、路由和第三方传输前终止请求。

建议的拒绝码包括：

| 错误码 | 含义 |
| --- | --- |
| `CONSUMER_UNKNOWN` | appKey 不存在 |
| `CONSUMER_DISABLED` | 调用方应用已停用 |
| `CREDENTIAL_EXPIRED` | 调用凭据不在有效期内 |
| `SIGNATURE_INVALID` | 请求签名不正确 |
| `REQUEST_REPLAYED` | nonce 已被使用或时间戳超窗 |
| `SERVICE_NOT_GRANTED` | 当前应用未获得 serviceCode 授权 |
| `GRANT_EXPIRED` | 服务授权已过期 |
| `SOURCE_NOT_ALLOWED` | 来源网络不符合规则 |
| `RATE_LIMITED` | 超过瞬时调用限制 |
| `QUOTA_EXCEEDED` | 超过周期配额 |
| `SCENARIO_NOT_ALLOWED` | 当前业务场景未获授权 |

## 7. 白名单与网络限制

IP 白名单只能作为附加条件，不能作为唯一身份认证方式。NAT、代理、容器出口和云网络调整都可能导致来源地址变化，而且源地址不能证明请求内容未被篡改。

推荐组合：

```text
appKey + HMAC 身份认证
  + serviceCode 服务授权
  + 可选 IP/CIDR 来源限制
```

对于安全要求更高的调用方，可以进一步叠加 mTLS 或 API 网关身份断言。

## 8. 数据库模型建议

### 8.1 `tpip_consumer_project`

| 字段 | 说明 |
| --- | --- |
| `id` | 主键 |
| `project_code` | 项目唯一编码 |
| `project_name` | 项目名称 |
| `owner_code` | 负责人 |
| `description` | 说明 |
| `status` | `ACTIVE`、`INACTIVE` |
| `row_version` | 乐观锁版本 |
| `created_at`、`updated_at` | 审计时间 |

### 8.2 `tpip_consumer_application`

| 字段 | 说明 |
| --- | --- |
| `id` | 主键 |
| `project_id` | 所属调用方项目 |
| `app_code` | 应用唯一编码 |
| `app_name` | 应用名称 |
| `owner_code` | 负责人 |
| `description` | 说明 |
| `status` | `ACTIVE`、`INACTIVE` |
| `row_version` | 乐观锁版本 |
| `created_at`、`updated_at` | 审计时间 |

### 8.3 `tpip_consumer_credential_version`

| 字段 | 说明 |
| --- | --- |
| `id` | 主键 |
| `application_id` | 调用方应用 |
| `version_no` | 平台自动生成的版本号 |
| `app_key` | 公开调用身份 |
| `secret_ref` | 签名密钥引用，不保存明文 |
| `algorithm` | 第一阶段固定 `HMAC_SHA256` |
| `valid_from`、`valid_until` | 有效期 |
| `lifecycle_status` | `DRAFT`、`PUBLISHED`、`REVOKED` |
| `content_checksum` | 不可变内容校验值 |
| `published_at`、`created_at` | 版本审计时间 |

允许新旧两个已发布凭据在受控时间窗内并行，以支持无停机轮换。撤销状态作为紧急安全操作单独审计。

### 8.4 `tpip_consumer_service_grant`

| 字段 | 说明 |
| --- | --- |
| `id` | 主键 |
| `application_id` | 获得授权的应用 |
| `operation_id` | TPIP Canonical Operation |
| `grant_code` | 授权资产编码 |
| `owner_code` | 负责人 |
| `status` | `ACTIVE`、`INACTIVE` |
| `row_version` | 乐观锁版本 |
| `created_at`、`updated_at` | 审计时间 |

建议建立 `application_id + operation_id` 唯一约束。

### 8.5 `tpip_consumer_service_grant_version`

| 字段 | 说明 |
| --- | --- |
| `id` | 主键 |
| `grant_id` | 服务授权资产 |
| `version_no` | 自动递增版本 |
| `valid_from`、`valid_until` | 授权有效期 |
| `qps_limit`、`burst_limit` | 瞬时限流 |
| `daily_quota` | 每日配额 |
| `allowed_cidrs` | 来源网络范围 JSON |
| `allowed_scenarios` | 允许业务场景 JSON |
| `routing_constraints` | 可选路由约束 JSON |
| `policy_document` | 高级受控策略 |
| `content_checksum` | 内容校验值 |
| `lifecycle_status` | `DRAFT`、`PUBLISHED` |
| `published_at`、`created_at` | 版本审计时间 |

### 8.6 `tpip_consumer_invocation_audit`

建议记录：

```text
request_id
application_id
app_key
service_code
grant_id
grant_version_id
authorization_result
reject_reason
selected_binding_id
response_outcome
duration_ms
created_at
```

审计表不得记录 Secret 明文和完整敏感报文。手机号、身份证号等字段按数据分级要求脱敏、摘要或不落库。

## 9. 服务授权与第三方路由的关系

授权决定“能否使用服务”，路由决定“服务由哪个第三方实现执行”：

```text
调用方应用
  → 是否允许调用 sms.send
      → TPIP 在阿里云、腾讯云、华为云实现中执行路由
```

默认禁止调用方直接指定第三方厂商，避免绕过平台的健康检查、权重、成本和故障切换治理。调用方只传递业务场景：

```http
X-TPIP-Scenario: login-verification
```

特殊情况下，授权策略可以通过标签约束可用实现：

```json
{
  "allowedTargetTags": ["transactional-sms"],
  "deniedProviders": ["provider-x"],
  "requiredRegion": "cn"
}
```

约束只能缩小平台路由的候选范围，不能允许调用方绕过服务授权。

## 10. 发布模型

调用授权采用与现有 TPIP 一致的设计态、发布态和运行态隔离：

```text
设计态
ConsumerApplication + ConsumerCredentialVersion + ConsumerServiceGrantVersion
  → 校验与发布
  → 编译 ConsumerAccessPlan
  → 冻结到 Bundle 或独立签名授权快照
  → Runtime 加载已发布快照
```

约束如下：

- Runtime 不直接查询设计态授权表；
- 已发布凭据和授权策略不允许原地修改；
- 普通变更通过新版本发布生效；
- 紧急停用、凭据撤销可使用独立实时阻断清单，但必须留下完整审计；
- 授权快照包含 appKey、服务授权、限制条件和 Secret Reference，不包含 Secret 原文；
- Bundle 或授权快照必须包含 checksum、发布时间和来源版本号。

若授权变化频率远高于接入实现发布频率，可以使用独立的 `ConsumerAccessSnapshot`，不必为每次授权变更重新编译完整集成 Bundle，但仍必须保持签名、不可变和可回滚。

## 11. 管理端交互

建议增加一级菜单“调用方管理”：

```text
调用方管理
  ├── 项目与应用
  ├── 服务授权
  ├── 调用凭据
  ├── 网络限制
  ├── 配额与限流
  └── 调用审计
```

普通配置流程：

```text
创建项目
  → 创建调用方应用
  → 选择允许调用的接入服务
  → 设置有效期、场景和调用额度
  → 创建 appKey 并绑定 appSecret 引用
  → 发布凭据和服务授权
  → 下载调用示例
```

在接入服务详情页反向展示已授权调用方，例如：

```text
发送短信 sms.send

已授权调用方：
- 会员中心生产服务
- 营销活动服务
- 订单中心
```

## 12. 与企业认证环境的演进

第一阶段由 TPIP 使用 HMAC 识别调用方。未来接入企业统一认证环境时，可以替换身份凭证校验方式，但保留“应用—服务授权”模型：

| 阶段 | 调用方身份认证 | 服务授权 |
| --- | --- | --- |
| 本地和早期项目 | appKey + HMAC-SHA256 | TPIP ConsumerServiceGrant |
| API 网关环境 | 网关签名或可信身份头 | TPIP ConsumerServiceGrant |
| OAuth2 环境 | Client Credentials JWT | TPIP ConsumerServiceGrant 或 Scope 映射 |
| 高安全环境 | OAuth2 + mTLS | TPIP ConsumerServiceGrant |

不能仅依赖 JWT 中存在某个用户名就默认开放所有服务。外部身份需要映射到 TPIP 调用方应用，再执行具体的服务授权判断。

## 13. 分阶段实施计划

### 13.1 第一阶段：调用身份与服务白名单闭环

实现：

- 调用方项目和应用；
- `appKey + HMAC-SHA256`；
- Secret Reference；
- 应用与 `serviceCode` 授权；
- 授权有效期；
- nonce 防重放；
- 基础调用审计；
- Runtime 已发布授权快照。

这是最小但完整的安全闭环，优先级最高。

### 13.2 第二阶段：消费治理

增加：

- QPS 和突发限流；
- 每日、每月配额；
- IP/CIDR 来源限制；
- 凭据无停机轮换；
- 按应用、项目和服务统计调用量、成功率和费用；
- 授权变更审批和到期提醒。

### 13.3 第三阶段：统一身份基础设施

增加：

- API 网关身份透传；
- OAuth2 Client Credentials；
- OIDC/JWT；
- mTLS；
- 企业租户与组织映射；
- 统一认证故障时的降级和紧急阻断策略。

## 14. 迁移建议

早期白名单数据可以按以下方式迁移：

```text
旧调用系统
  → ConsumerProject
  → ConsumerApplication

旧系统 + serviceCode 白名单
  → ConsumerServiceGrant
  → 首个 PUBLISHED GrantVersion

旧 appKey/appSecret
  → ConsumerCredentialVersion
  → appSecret 迁移到 Secret 存储，只保留 Secret Reference
```

迁移期间可以短期兼容旧鉴权入口，但新旧入口必须产生相同格式的授权审计。兼容期结束后停用旧白名单读取逻辑，避免形成两套权限事实来源。

## 15. 决策结论

TPIP 保留早期“白名单限制”的业务思想，但正式产品名称调整为“调用方服务授权”。平台采用“调用方项目 → 调用方应用 → 调用凭据 → 服务授权 → 授权策略版本”的模型。

第一阶段以 `appKey + HMAC-SHA256 + serviceCode 授权` 落地；IP 白名单作为附加限制；调用方不能直接指定第三方厂商；Runtime 只读取已发布、不可变、可审计的授权快照。未来引入 OAuth2、OIDC/JWT、API 网关或 mTLS 时，仅替换身份认证机制，不推翻服务授权模型。

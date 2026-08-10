# TPIP Runtime Pipeline v0.1

## 1. 定位

Runtime Pipeline 是不可变 Bundle 的执行器。它不读取设计态数据库，不解释 Mapping 或 Policy
源配置，只执行 Bundle 中已经冻结和校验过的 IR、契约快照与 Endpoint 快照。

## 2. 固定执行顺序

```text
Deployment确定性选路
  -> Bundle解析与完整性校验
  -> Canonical Request契约校验
  -> BEFORE_REQUEST_MAPPING Policy
  -> OUTBOUND_REQUEST Mapping IR
  -> AFTER_REQUEST_MAPPING Policy
  -> Provider Request契约校验
  -> BEFORE_TRANSPORT Policy
  -> HTTP Transport
  -> 非2xx: ON_PROVIDER_ERROR Policy -> 标准失败结果
  -> AFTER_TRANSPORT Policy
  -> Provider Response契约校验
  -> BEFORE_RESPONSE_MAPPING Policy
  -> INBOUND_RESPONSE Mapping IR
  -> AFTER_RESPONSE_MAPPING Policy
  -> Canonical Response契约校验
  -> 标准InvocationResponse
```

任何请求契约、请求 Mapping、Policy 或 Provider Request 校验错误都会在 HTTP Transport 之前终止。
Bundle 与路由解析错误保持 HTTP 503；Bundle 已成功选定后的执行错误进入标准
`InvocationResponse.result`，避免把第三方报文直接泄漏给调用方。

## 3. 稳定错误码

| 错误码 | 含义 | 默认是否可重试 |
| --- | --- | --- |
| `DEADLINE_EXCEEDED` | 调用进入流水线前已超过 deadline | 否 |
| `CANONICAL_REQUEST_INVALID` | 标准请求不满足已发布契约 | 否 |
| `REQUEST_MAPPING_FAILED` | 请求 Mapping IR 执行失败 | 否 |
| `PROVIDER_REQUEST_INVALID` | 映射结果不满足第三方请求契约 | 否 |
| `POLICY_EXECUTION_FAILED` | Policy Provider 执行失败 | 取决于策略配置，v0.1 默认否 |
| `TRANSPORT_FAILED` | 建连、超时、协议或响应解析失败 | 是 |
| `PROVIDER_HTTP_ERROR` | 第三方返回非 2xx | 5xx 可重试，4xx 默认不可重试 |
| `PROVIDER_RESPONSE_INVALID` | 第三方响应不满足冻结契约 | 否 |
| `RESPONSE_MAPPING_FAILED` | 响应 Mapping IR 执行失败 | 否 |
| `CANONICAL_RESPONSE_INVALID` | 标准响应不满足已发布契约 | 否 |
| `PIPELINE_FAILED` | 未分类的运行时故障 | 否，需人工分析 |

当前公共响应只暴露稳定错误码和安全消息；可重试标记与详细 diagnostics 保留在运行时异常模型中，
后续进入结构化审计和指标系统，不直接向业务调用方泄漏内部细节。

## 4. HTTP Transport 安全边界

- 仅允许 Bundle 中冻结的 `http` 或 `https` Endpoint；调用请求不能覆盖目标地址。
- 不跟随重定向，防止目标被第三方响应动态改写。
- 拒绝 `Host`、`Content-Length`、`Connection`、`Transfer-Encoding` 等受限头注入。
- 使用 Endpoint 冻结的 connect/read/total timeout，并限制响应体大小。
- v0.1 只接受 JSON 响应；空响应表示 JSON `null`。
- Secret 原文不进入 Bundle。API Key 由 Secret Resolver 在 `BEFORE_TRANSPORT` 阶段按引用解析，
  敏感 Header 在 Transport 完成或失败后立即从调用上下文移除。

## 5. Runtime JSON Schema Profile 0.1

当前执行器支持结构化契约常用关键字：

```text
type, required, properties, items, enum, const, additionalProperties,
minLength, maxLength, pattern, minimum, maximum, minItems, maxItems
```

支持 `$schema`、`$id`、`title`、`description`、`default`、`examples` 等注解关键字。对于未进入
Runtime Profile 的断言关键字采取 fail-closed，不会静默放过无法执行的约束。Deployment 预热会
提前验证 Schema Profile 兼容性，不会等到首次业务调用时发现。

## 6. Policy Provider v0.1

当前可执行 Provider 包括 `builtin.transport.inject@1.x` 和 `builtin.auth.api-key@1.x`。
前者用于把受控上下文值注入 HTTP Header：

```yaml
AFTER_REQUEST_MAPPING:
  - id: inject-request-id
    use: builtin.transport.inject@1.0.0
    with:
      headers:
        X-Request-Id: "${context.requestId}"
```

API Key 策略示例：

```yaml
BEFORE_TRANSPORT:
  - id: provider-api-key
    use: builtin.auth.api-key@1.0.0
    with:
      secretRef: env://TPIP_SECRET_PROVIDER_A_API_KEY
      headerName: X-API-Key
      prefix: "ApiKey "
    onFailure: FAIL
```

运行时支持 `context`、`canonical`、`provider` 和 `transport.statusCode` 的只读插值；未知 Provider
会 fail-closed。OAuth2、签名、摘要、加解密和结果表达式必须各自实现受控 Provider，禁止以
Groovy、JavaScript 或表达式调用任意 Java 方法作为兜底。

## 7. Secret Resolver 与预热门禁

- `SecretResolver` 只接收 Bundle 中已声明的 Secret Reference。
- `SecretValue` 使用可清零字符数组保存解析结果，关闭后不可再次读取。
- v0.1 的 `EnvironmentSecretResolver` 只允许 `env://TPIP_SECRET_*`，不能读取任意系统环境变量。
- API Key 的明文只在构造 HTTP Header 时短暂变成字符串；不会进入 Bundle、数据库、响应或诊断。
- 生产环境需要实现 Vault、AWS Secrets Manager、Azure Key Vault 或 GCP Secret Manager 适配器。
- 预热必须通过 `SCHEMA_PROFILE_COMPATIBLE`、`ENDPOINT_COMPATIBLE`、
  `POLICY_PROVIDERS_AVAILABLE`、`SECRET_REFERENCES_AVAILABLE` 四项检查。
- 预热失败返回 `TPIP_RUNTIME_PREFLIGHT_FAILED`，控制面将实例失败写入 Deployment 证据。

## 8. 下一阶段门禁

1. 接入生产级 Secret Manager，增加租户、环境、用途和工作负载身份授权。
2. 为每个新增 Policy Provider 建立确定性、副作用、幂等和超时测试。
3. 增加调用审计、阶段耗时、第三方状态码、错误码和 Bundle/Deployment 标签指标。
4. 增加连接池、并发隔离、熔断、重试预算和幂等约束，重试不得由任意 Policy 自行无限循环。

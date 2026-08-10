# Workspace REMOTE_CALL Fixture v0.1

## 1. 目标与边界

REMOTE_CALL Fixture 用候选 BindingVersion 生成不可变候选 Bundle，并通过与 Runtime 相同的 Pipeline 对第三方
测试端点发起一次真实调用。它用于发布前验证契约、映射、Policy、Secret Reference、Transport 和第三方响应，
不是通用接口调试器，也不能绕过 Workspace 发布门禁。

功能默认关闭。`MAPPING` 始终是默认执行模式，历史 Fixture 不会因为升级而产生网络调用。

## 2. 完整执行链

```text
Fixture canonical source
  -> Canonical Request Schema
  -> Request Mapping
  -> Policy DSL（Header 注入、Secret Reference 解析）
  -> Provider Request Schema
  -> 受控 HTTP Transport（禁止重定向）
  -> 捕获 Status / Header / Provider Body / Duration
  -> Provider Response Schema
  -> Response Mapping
  -> Canonical Response Schema
  -> Fixture Assertion Profile
  -> 脱敏 VerificationCheck Evidence
```

控制面不实现第二套调用语义，而是复用 `DefaultRuntimePipeline`、Mapping Engine、Policy Executor、Contract
Validator 和 JDK HTTP Transport。候选 Bundle 只存在于本次验证内存中，不会注册成可路由的生产 Deployment。

## 3. Fixture 配置

```json
{
  "caseCode": "remote.success",
  "caseName": "Candidate pipeline remote verification",
  "caseOrder": 30,
  "executionMode": "REMOTE_CALL",
  "direction": "OUTBOUND_REQUEST",
  "source": {"customerId": "C1001"},
  "assertions": [
    {"code":"runtime-success","type":"SUCCESS","expected":true},
    {"code":"http-status","type":"HTTP_STATUS","expected":200},
    {"code":"content-type","type":"HTTP_HEADER","name":"Content-Type","operator":"CONTAINS","expected":"application/json"},
    {"code":"member-id","type":"JSON_PATH","path":"$.data.member_no","operator":"EQUALS","expected":"C1001"}
  ]
}
```

`source` 是 Canonical Request。HTTP/JSONPath/JSON Schema 断言面向原始 Provider Response；`SUCCESS` 和
`DIAGNOSTIC_CODE` 面向完整 Runtime Pipeline 结论。

## 4. 强制安全门禁

一次调用必须同时满足：

1. 全局 `enabled=true`；
2. Fixture 显式声明 `executionMode=REMOTE_CALL`；
3. BindingVersion 已发布且 `idempotencyClass=IDEMPOTENT`；
4. Endpoint environment、host 和 port 同时进入白名单；
5. 非 loopback HTTP 被拒绝，远程主机必须使用 HTTPS；
6. Endpoint total timeout 不超过平台上限；
7. 单次请求、响应大小和单 Run 调用数量不超过上限；
8. HTTP 重定向关闭，Host、Content-Length、Connection 等危险 Header 禁止覆盖；
9. Secret 只允许通过已声明的 Secret Reference 和 Resolver 获取，不进入 Fixture 或证据。

`IDEMPOTENT_WITH_KEY`、`NON_IDEMPOTENT`、`UNKNOWN` 当前全部拒绝。后续若支持带幂等键操作，必须先增加
Fixture 的幂等键生成与重放审计标准。

## 5. 配置

```text
TPIP_WORKSPACE_REMOTE_CALL_ENABLED=false
TPIP_WORKSPACE_REMOTE_CALL_ALLOWED_ENVIRONMENTS=test,local,dev
TPIP_WORKSPACE_REMOTE_CALL_ALLOWED_HOSTS=
TPIP_WORKSPACE_REMOTE_CALL_ALLOWED_PORTS=
TPIP_WORKSPACE_REMOTE_CALL_MAXIMUM_CALLS=20
TPIP_WORKSPACE_REMOTE_CALL_MAXIMUM_REQUEST_BYTES=262144
TPIP_WORKSPACE_REMOTE_CALL_MAXIMUM_RESPONSE_BYTES=1048576
TPIP_WORKSPACE_REMOTE_CALL_MAXIMUM_TIMEOUT=5s
```

本地 Customer Lookup 验收使用：

```text
TPIP_WORKSPACE_REMOTE_CALL_ENABLED=true
TPIP_WORKSPACE_REMOTE_CALL_ALLOWED_HOSTS=127.0.0.1
TPIP_WORKSPACE_REMOTE_CALL_ALLOWED_PORTS=19090
TPIP_SECRET_E2E_PROVIDER_API_KEY=local-e2e-key
```

## 6. 证据脱敏

VerificationCheck 只保存 HTTP status、耗时、请求/响应字节数、响应 Body SHA-256、响应 Header 名称和断言结论。
不保存请求 Header、Secret、原始 Provider Body 或 Header 值。JSONPath、HTTP_HEADER 和 Policy 断言的实际值仅保存
SHA-256；状态码、成功状态、诊断码和 Schema violation 可明文保存。

## 7. 存储与兼容

迁移 `V25__fixture_remote_call_execution_mode.sql` 增加 `execution_mode`，默认值为 `MAPPING`。因此历史数据、
已有 FixtureSuiteVersion 和旧 E2E 行为保持不变。执行模式与断言一样进入版本内容 checksum。

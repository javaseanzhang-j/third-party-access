# TPIP 本地端到端场景验收方案

## 1. 验收目标

本地端到端验收不是简单确认接口返回 HTTP 200，而是验证以下完整链路：

```text
标准契约
  → 第三方契约
  → JSONPath双向映射
  → Policy
  → Endpoint
  → Binding
  → Workspace验证与审批
  → Bundle编译发布
  → Deployment预热与激活
  → Runtime调用
  → 指标与审计证据
```

验收应使用确定性、可故障注入的本地第三方 Mock，不直接依赖真实厂商接口。所有资产通过 Control Plane API 创建，不直接写数据库业务表。

## 2. 标准验收场景

场景编码：

```text
e2e.customer.lookup
```

业务系统使用标准字段查询客户：

```json
{
  "customerId": "C1001"
}
```

模拟第三方接收：

```json
{
  "member_no": "C1001"
}
```

模拟第三方返回：

```json
{
  "code": "0",
  "data": {
    "member_no": "C1001",
    "member_name": "张三",
    "mobile_no": "13800138000",
    "member_status": "ACTIVE"
  }
}
```

TPIP 转换后的标准响应：

```json
{
  "customerId": "C1001",
  "customerName": "张三",
  "mobile": "13800138000",
  "status": "ACTIVE"
}
```

该场景用于验证：同一业务含义在不同第三方报文中使用不同字段时，通过配置化 JSONPath 映射转换为稳定业务标准字段。

## 3. 验收目录

```text
e2e/
└── customer-lookup/
    ├── README.md
    ├── mock-provider/
    │   ├── pom.xml
    │   └── src/
    ├── assets/
    │   ├── domain.json
    │   ├── capability.json
    │   ├── operation.json
    │   ├── canonical-request-schema.json
    │   ├── canonical-response-schema.json
    │   ├── provider-request-schema.json
    │   ├── provider-response-schema.json
    │   ├── outbound-mapping.json
    │   ├── inbound-mapping.json
    │   ├── policy.json
    │   ├── endpoint.json
    │   └── binding.json
    ├── scripts/
    │   ├── check-infrastructure.sh
    │   ├── bootstrap-assets.sh
    │   ├── publish-bundle.sh
    │   ├── deploy.sh
    │   ├── invoke-success.sh
    │   ├── invoke-failures.sh
    │   └── verify.sh
    └── evidence/
        └── .gitkeep
```

验收工具、资产模板和证据不得混入正式业务模块。Mock Provider 优先使用 Java 自带 `HttpServer`，减少额外框架依赖。

## 4. Mock Provider

监听地址：

```text
http://127.0.0.1:19090
```

第三方接口：

```text
POST /vendor/v1/members/query
```

要求请求头：

```text
X-API-Key: ApiKey local-e2e-key
X-Request-Id: {requestId}
```

故障注入规则：

| member_no | 行为 |
| --- | --- |
| `C1001` | 正常返回 |
| `C404` | 返回 HTTP 404 |
| `C500` | 返回 HTTP 503 |
| `CSLOW` | 延迟超过 Runtime 读取超时 |
| `CBAD` | 返回 HTTP 200，但报文不满足第三方响应 Schema |
| `CAUTH` | API Key 错误时返回 HTTP 401 |

管理接口：

```text
GET  /mock/health
GET  /mock/admin/stats
POST /mock/admin/reset
```

统计接口用于证明：

- 标准请求校验失败时没有调用第三方；
- Mapping 失败时没有调用第三方；
- Policy 失败时没有调用第三方；
- 正常请求只调用一次；
- Request ID 已正确注入。

## 5. 标准契约

### 5.1 标准请求 Schema

```json
{
  "type": "object",
  "required": ["customerId"],
  "properties": {
    "customerId": {
      "type": "string",
      "minLength": 1,
      "maxLength": 40
    }
  },
  "additionalProperties": false
}
```

### 5.2 标准响应 Schema

```json
{
  "type": "object",
  "required": ["customerId", "customerName", "mobile", "status"],
  "properties": {
    "customerId": {"type": "string"},
    "customerName": {"type": "string"},
    "mobile": {"type": "string"},
    "status": {
      "type": "string",
      "enum": ["ACTIVE", "DISABLED"]
    }
  },
  "additionalProperties": false
}
```

## 6. 第三方契约

### 6.1 第三方请求 Schema

```json
{
  "type": "object",
  "required": ["member_no"],
  "properties": {
    "member_no": {"type": "string"}
  },
  "additionalProperties": false
}
```

### 6.2 第三方响应 Schema

```json
{
  "type": "object",
  "required": ["code", "data"],
  "properties": {
    "code": {
      "type": "string",
      "const": "0"
    },
    "data": {
      "type": "object",
      "required": ["member_no", "member_name", "mobile_no", "member_status"],
      "properties": {
        "member_no": {"type": "string"},
        "member_name": {"type": "string"},
        "mobile_no": {"type": "string"},
        "member_status": {"type": "string"}
      }
    }
  }
}
```

## 7. JSONPath 映射

### 7.1 出站请求

```text
$.customerId → $.member_no
```

规则示例：

```json
{
  "ruleCode": "customer-id-to-member-no",
  "ruleOrder": 10,
  "valueSource": "SELECTOR",
  "sourceSelector": "$.customerId",
  "targetSelector": "$.member_no",
  "targetType": "STRING",
  "required": true,
  "missingStrategy": "FAIL",
  "errorStrategy": "FAIL",
  "enabled": true
}
```

### 7.2 入站响应

```text
$.data.member_no     → $.customerId
$.data.member_name   → $.customerName
$.data.mobile_no     → $.mobile
$.data.member_status → $.status
```

Mapping 版本创建后必须调用：

```text
POST /control/v1/mappings/{id}/versions/{versionId}:test
```

Fixture 测试通过后才能发布 Mapping 版本。

Mapping 版本中的 Schema 引用必须在配置阶段解析并冻结到真实数据库版本，不得把逻辑 URI
直接写入 Mapping：

```text
canonical-contract-version:{contractId}:{versionId}
provider-contract-version:{contractId}:{versionId}
```

出站 Mapping 使用 Canonical Request 版本作为源、Provider Contract 版本作为目标；入站
Mapping 使用 Provider Contract 版本作为源、Canonical Response 版本作为目标。

## 8. Policy

### 8.1 Request ID 注入

```json
{
  "id": "inject-request-id",
  "use": "builtin.transport.inject@1.0.0",
  "with": {
    "headers": {
      "X-Request-Id": "${context.requestId}"
    }
  },
  "onFailure": "FAIL"
}
```

### 8.2 API Key

```json
{
  "id": "provider-api-key",
  "use": "builtin.auth.api-key@1.0.0",
  "with": {
    "secretRef": "env://TPIP_SECRET_E2E_PROVIDER_API_KEY",
    "headerName": "X-API-Key",
    "prefix": "ApiKey "
  },
  "onFailure": "FAIL"
}
```

Runtime 启动前设置：

```bash
export TPIP_SECRET_E2E_PROVIDER_API_KEY=local-e2e-key
```

Secret 原文不得进入数据库、Bundle、日志和API响应。

## 9. Endpoint

```json
{
  "endpointCode": "e2e.customer.member-query",
  "environmentCode": "test",
  "protocolScheme": "HTTP",
  "baseUrl": "http://127.0.0.1:19090",
  "resourcePath": "/vendor/v1/members/query",
  "httpMethod": "POST",
  "contentType": "application/json",
  "charsetName": "UTF-8",
  "connectTimeoutMs": 1000,
  "readTimeoutMs": 2000,
  "totalTimeoutMs": 3000
}
```

Endpoint 发布前执行 Probe，确认 Mock Provider 可访问。

## 10. 资产编码

统一使用：

```text
e2e.customer.*
```

建议编码：

```text
Domain:           e2e.customer
Capability:       e2e.customer.profile
Operation:        e2e.customer.lookup
Provider:         e2e.mock-provider
ProviderContract: e2e.mock.customer.lookup
Binding:          e2e.customer.lookup.mock
Outbound Mapping: e2e.customer.lookup.outbound
Inbound Mapping:  e2e.customer.lookup.inbound
Policy:           e2e.customer.lookup.policy
Endpoint:         e2e.customer.member-query
```

已发布资产不可原地修改。每次独立验收使用新的运行编号或版本：

```text
e2e.customer.lookup.20260809-01
```

不通过删除数据库记录重置验收环境。

## 11. 资产配置顺序

```text
1. 创建 Provider
2. 创建 Credential Reference
3. 创建 Business Domain
4. 创建 Capability
5. 创建 Canonical Operation
6. 创建 Canonical Request Contract
7. 创建 Canonical Response Contract
8. 发布 Canonical Contract 版本
9. 创建 Provider Contract
10. 创建 Provider Request/Response Contract 版本
11. 发布 Provider Contract 版本
12. 创建 Endpoint Revision
13. 发布 Endpoint
14. 创建 Binding
15. 创建 Outbound Mapping 及版本
16. Fixture 测试并发布 Outbound Mapping
17. 创建 Inbound Mapping 及版本
18. Fixture 测试并发布 Inbound Mapping
19. 创建 Policy 及版本
20. 发布 Policy 版本
21. 创建 Binding Version 并冻结依赖
22. 查看 Bundle Preview
```

以上步骤必须通过 Control Plane API 执行，Runner 动态记录生成的资产 ID，不能依赖固定数据库主键。

## 12. Workspace 与 Bundle

```text
创建 Workspace
  → 加入 Binding Version
  → 执行 Verification
  → Submit Review
  → Approval
  → 编译 Bundle
  → 发布 Bundle
```

需要记录：

- Workspace ID；
- Verification ID；
- Approval ID；
- Bundle ID；
- Bundle Code；
- Bundle Version；
- Bundle Checksum；
- Artifact URI。

保存到：

```text
e2e/customer-lookup/evidence/{runId}/asset-ids.json
```

## 13. Deployment

```text
创建 Deployment
  → Runtime 预热
  → 检查预热门禁
  → 激活 Deployment
  → 设置 100% 流量
  → 查询动态路由
```

预热至少确认：

```text
SCHEMA_PROFILE_COMPATIBLE
ENDPOINT_COMPATIBLE
POLICY_PROVIDERS_AVAILABLE
SECRET_REFERENCES_AVAILABLE
```

任一项失败时不得激活。

本地端口建议：

```text
Control Plane: 18080
Runtime:       18081
Worker:        8082
Mock Provider: 19090
MySQL:         3306
Redis:         6379
```

## 14. Runtime 调用

接口：

```text
POST /integration/v1/operations/e2e.customer.lookup:invoke
```

请求：

```json
{
  "meta": {
    "requestId": "e2e-req-001",
    "caller": "local-e2e",
    "tenantId": null,
    "idempotencyKey": null,
    "deadline": null,
    "attributes": {
      "traceId": "e2e-trace-001"
    }
  },
  "payload": {
    "customerId": "C1001"
  }
}
```

成功响应必须包含：

```json
{
  "result": {
    "success": true,
    "code": "SUCCESS",
    "message": "处理成功"
  },
  "payload": {
    "customerId": "C1001",
    "customerName": "张三",
    "mobile": "13800138000",
    "status": "ACTIVE"
  }
}
```

响应元数据还应保留 Request ID、Trace ID、Operation Code、Bundle Version、Provider Code 和耗时。

## 15. 验收用例

| 编号 | 场景 | 预期 |
| --- | --- | --- |
| E2E-001 | 正常查询 | 标准响应字段转换正确 |
| E2E-002 | 缺少 `customerId` | `CANONICAL_REQUEST_INVALID` |
| E2E-003 | 包含多余非法字段 | 标准请求校验失败 |
| E2E-004 | Secret不可解析 | `POLICY_EXECUTION_FAILED` |
| E2E-005 | API Key错误 | `PROVIDER_HTTP_ERROR` |
| E2E-006 | 第三方404 | `PROVIDER_HTTP_ERROR`，不泄漏第三方原始报文 |
| E2E-007 | 第三方503 | `PROVIDER_HTTP_ERROR` |
| E2E-008 | 第三方超时 | `TRANSPORT_FAILED` |
| E2E-009 | 第三方200但响应格式错误 | `PROVIDER_RESPONSE_INVALID` |
| E2E-010 | 响应Mapping源字段缺失 | `RESPONSE_MAPPING_FAILED`或第三方契约失败 |
| E2E-011 | Request ID注入 | Mock收到正确Header |
| E2E-012 | Bundle缓存 | Control Plane短暂不可用时按LKG规则工作 |
| E2E-013 | Bundle被篡改 | Checksum校验失败 |
| E2E-014 | Deployment回滚 | 路由恢复到上一稳定版本 |

E2E-002和E2E-003必须同时确认 Mock Provider 调用次数没有增加，证明请求在 Transport 前终止。

## 16. 验收证据

```text
e2e/customer-lookup/evidence/{runId}/
├── environment.json
├── asset-ids.json
├── mapping-outbound-result.json
├── mapping-inbound-result.json
├── bundle-preview.json
├── bundle-published.json
├── preheat-result.json
├── deployment-result.json
├── invocation-success.json
├── invocation-failures.json
├── mock-provider-stats.json
├── runtime-metrics.txt
└── acceptance-summary.md
```

验收摘要至少记录：

- 执行时间和运行编号；
- Java版本；
- Flyway数据库版本；
- Bundle版本和Checksum；
- Deployment ID；
- 成功和失败用例数；
- 每个失败用例的稳定错误码；
- 未解决问题；
- 是否允许进入下一阶段。

## 17. 通过标准

必须同时满足：

- 所有资产通过 Control Plane API 配置；
- 未直接修改数据库业务表；
- Mapping Fixture 全部通过；
- 已发布资产未原地修改；
- Bundle Preview和正式Bundle依赖闭包一致；
- Runtime只读取已发布Bundle；
- Secret原文未进入数据库、Bundle、日志和响应；
- 正常请求字段映射完全正确；
- 非法请求没有调用第三方；
- 第三方异常被转换为稳定错误码；
- 部署预热、激活、路由和回滚可以验证；
- 指标包含Operation、Provider、Bundle和结果标签；
- 自动归档保持关闭；
- 在线证据清理保持关闭；
- 验收证据完整且可重复查看。

## 18. 实施交付物

最终交付一个可重复执行的本地E2E验收包：

```text
Java Mock Provider
  + 资产JSON模板
  + API Bootstrap Runner
  + Invocation Runner
  + 自动断言
  + Markdown验收报告
```

后续修改 Mapping Engine、Policy DSL、Bundle、Deployment 或 Runtime Pipeline 后，都应执行该验收包，确认整条第三方接入链路未被破坏。

## 19. 当前延期边界

本地 MinIO 与 OIDC/JWT/RBAC 当前均为 `DEFERRED / ON_DEMAND`，不作为本验收的前置条件。验收期间继续使用：

- 本地 `FILESYSTEM` 归档适配器；
- 本地单用户 `X-Operator` 审计标签；
- 受限环境变量 Secret Resolver。

自动归档和在线证据清理不得因执行E2E验收而开启。

# TPIP 客户资料查询：全流程 UI 配置实例

> 目标：完全通过 TPIP 页面，配置一个“根据客户编号查询客户资料”的第三方接入，并最终从 Runtime 调用成功。  
> 适用环境：本地单用户、`test` 环境。  
> 示例第三方：项目自带的本地 Member Center Mock Provider。  
> 示例不是自动化脚本生成的数据，所有名称和编码都以人工理解为优先。

## 1. 完成后会得到什么

业务系统提交稳定的标准请求：

```json
{
  "customerId": "C1001"
}
```

第三方系统实际接收：

```json
{
  "member_no": "C1001"
}
```

第三方系统返回：

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

TPIP 转换后，业务系统收到：

```json
{
  "customerId": "C1001",
  "customerName": "张三",
  "mobile": "13800138000",
  "status": "ACTIVE"
}
```

这里最重要的价值是：业务系统永远使用 `customerId/customerName/mobile/status`，不需要知道第三方使用
`member_no/member_name/mobile_no/member_status`。

## 2. 先理解本实例中的资产

数据库中现有的大量 `e2e.*.202608...` 资产来自自动化验收：

- `e2e` 表示端到端测试，不是业务域；
- 末尾时间戳表示某次自动化运行，用于隔离数据和追踪证据；
- `#18` 之类数字是数据库 ID，只用于关联；
- 这些数据可以用于证明平台能力，但不建议作为人工配置时的命名模板；
- 学习本教程时只查找下表中的中文名称和稳定编码，不需要理解或修改已有 E2E 资产。

| TPIP 资产 | 本实例中的含义 | 人工可读编码 |
| --- | --- | --- |
| Domain | 客户领域 | `customer` |
| Capability | 客户资料能力 | `customer.profile` |
| Operation | 根据客户编号查询资料 | `customer.profile.lookup` |
| Canonical Contract | 业务系统使用的标准请求和响应 | `customer.profile.lookup.request/response` |
| Provider | 本地模拟会员中心 | `local.member-center` |
| ProviderContract | 会员中心的会员查询协议 | `local.member-center.member-query` |
| Endpoint | 会员查询接口在 test 环境的地址 | `local.member-center.member-query` |
| Binding | 标准客户查询由本地会员中心实现 | `customer.profile.lookup.local-member-center` |
| Mapping | 标准字段与第三方字段的双向转换 | `customer.profile.lookup.*` |
| Policy | Request ID 和 API Key 注入 | `customer.profile.lookup.transport-policy` |
| BindingVersion | 一次可执行依赖闭包 | Revision 1 |
| FixtureSuite | 可重复执行的映射验收用例 | `customer.profile.lookup.fixtures` |
| Workspace | 本次 test 环境发布候选 | `customer.profile.lookup.release-001` |
| Bundle | 经过验证和批准的运行制品 | `customer.profile.lookup.local-member-center@1.0.0` |
| Deployment | Bundle 的一次运行部署 | `customer.profile.lookup.test-v1` |

命名规则：

- `Name/名称` 使用中文或易懂英文，给人阅读；
- `Code/编码` 使用稳定业务语义，给系统识别；
- 环境放在 `environmentCode`，不要写入 Operation；
- 变化通过新 Version/Revision 表达，不在编码后拼时间戳；
- 如果本文编码已经被创建，请复用已有稳定资产，不要重复创建；需要重新练习时，仅给 Workspace、Bundle 或
  Deployment 使用新的序号。

## 3. 启动前准备

### 3.1 端口

本文按当前 UI 默认代理拓扑编写：

| 进程 | 端口 |
| --- | --- |
| Control Plane | `18082` |
| Runtime | `18081` |
| Worker | `8082` |
| UI | `18100` |
| Mock Provider | `19090` |

如果 Control Plane 启动在 `18080`，而 UI 仍代理到 `18082`，页面会显示 `CONTROL OFFLINE`。开始配置前必须先统一端口。

### 3.2 启动 Mock Provider

在工程根目录执行：

```bash
python3 e2e/customer-lookup/mock-provider.py
```

浏览器或终端访问以下地址应得到 `{"status":"UP"}`：

```text
http://127.0.0.1:19090/mock/health
```

### 3.3 Secret 环境变量

本实例使用的 Secret Reference 是：

```text
env://TPIP_SECRET_E2E_PROVIDER_API_KEY
```

对应的本地模拟值是：

```bash
export TPIP_SECRET_E2E_PROVIDER_API_KEY=local-e2e-key
```

至少需要在 Runtime 启动前设置。为了让验证、预热和本地调试环境一致，建议 Control Plane 也设置同一环境变量。
如果 Java 进程已经启动，后来才执行 `export` 不会生效，必须重启相应 Java 进程。

### 3.4 页面入口

打开：

```text
http://127.0.0.1:18100/integration-assets/wizard
```

向导用于观察进度和定位缺口；实际表单配置在左侧“集成配置”的专家页面完成。

## 4. 第一步：建立业务标准接口

进入：`集成配置 → 业务标准接口`。

### 4.1 创建 Domain

切换到 `Domain` 页签，点击 `＋ 新增 Domain`：

| 字段 | 填写值 |
| --- | --- |
| Domain 编码 | `customer` |
| 名称 | `客户领域` |
| 负责人 | `customer-team` |
| 说明 | `客户主数据与客户资料相关业务能力` |

成功标志：列表中出现“客户领域 / `customer`”。

### 4.2 创建 Capability

切换到 `Capability` 页签，点击 `＋ 新增 Capability`：

| 字段 | 填写值 |
| --- | --- |
| Domain | `客户领域` |
| Capability 编码 | `customer.profile` |
| 名称 | `客户资料` |
| 负责人 | `customer-team` |
| 说明 | `查询与维护客户基础资料` |

### 4.3 创建 Operation

切换到 `Operation` 页签，点击 `＋ 新增 Operation`：

| 字段 | 填写值 |
| --- | --- |
| Capability | `客户资料` |
| Operation 编码 | `customer.profile.lookup` |
| 名称 | `客户资料查询` |
| 调用模式 | `SYNC` |
| 幂等分类 | `IDEMPOTENT` |
| 数据分级 | `INTERNAL` |
| 负责人 | `customer-team` |
| 说明 | `根据客户编号查询客户名称、手机号和状态` |

不要在 Operation 中出现 `local`、`vendor`、`test` 或版本号。Operation 表达的是长期稳定的业务语义。

### 4.4 创建 Canonical Request Contract

切换到 `Canonical Contract` 页签，点击 `＋ 新增 Contract`：

| 字段 | 填写值 |
| --- | --- |
| Operation | `客户资料查询` |
| Contract 编码 | `customer.profile.lookup.request` |
| 名称 | `客户资料查询请求` |
| 类型 | `REQUEST` |
| 说明 | `业务系统提交的标准客户查询请求` |

### 4.5 创建并发布 Request ContractVersion

切换到 `ContractVersion` 页签，选择“客户资料查询请求”，点击 `＋ 新增 Version`。

| 字段 | 填写值 |
| --- | --- |
| 语义版本 | `1.0.0` |
| 兼容策略 | `BACKWARD` |

Schema Document：

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

Example Document：

```json
{
  "customerId": "C1001"
}
```

创建后状态是 `DRAFT`。确认内容无误后点击 `发布`，状态必须变成 `PUBLISHED`。

### 4.6 创建 Canonical Response Contract

回到 `Canonical Contract` 页签：

| 字段 | 填写值 |
| --- | --- |
| Operation | `客户资料查询` |
| Contract 编码 | `customer.profile.lookup.response` |
| 名称 | `客户资料查询响应` |
| 类型 | `RESPONSE` |
| 说明 | `业务系统接收的标准客户资料` |

### 4.7 创建并发布 Response ContractVersion

语义版本：`1.0.0`，兼容策略：`BACKWARD`。

Schema Document：

```json
{
  "type": "object",
  "required": ["customerId", "customerName", "mobile", "status"],
  "properties": {
    "customerId": { "type": "string" },
    "customerName": { "type": "string" },
    "mobile": { "type": "string" },
    "status": {
      "type": "string",
      "enum": ["ACTIVE", "DISABLED"]
    }
  },
  "additionalProperties": false
}
```

Example Document：

```json
{
  "customerId": "C1001",
  "customerName": "张三",
  "mobile": "13800138000",
  "status": "ACTIVE"
}
```

创建后执行发布。

本阶段成功标志：向导中的“业务标准接口”显示 `已完成`。

## 5. 第二步：配置第三方系统

进入：`集成配置 → 第三方系统`。

### 5.1 创建 Provider

在 `Provider` 页签点击 `＋ 新增 Provider`：

| 字段 | 填写值 |
| --- | --- |
| Provider 编码 | `local.member-center` |
| 名称 | `本地会员中心` |
| 类型 | `PLATFORM` |
| 负责人 | `integration-team` |
| 说明 | `用于人工学习 TPIP 配置流程的本地模拟会员中心` |

Provider 只是第三方系统身份，不包含 URL 和环境。

### 5.2 创建 CredentialRef

切换到 `CredentialRef` 页签，点击 `＋ 新增 CredentialRef`：

| 字段 | 填写值 |
| --- | --- |
| Provider | `本地会员中心` |
| Credential 编码 | `local.member-center.api-key` |
| 环境 | `test` |
| 类型 | `API_KEY` |
| Secret Reference | `env://TPIP_SECRET_E2E_PROVIDER_API_KEY` |

这里只登记引用，绝对不要填写 `local-e2e-key`。

### 5.3 创建 ProviderContract

切换到 `ProviderContract` 页签：

| 字段 | 填写值 |
| --- | --- |
| Provider | `本地会员中心` |
| Contract 编码 | `local.member-center.member-query` |
| 名称 | `会员资料查询协议` |
| 协议 | `HTTP` |
| 说明 | `通过会员编号查询会员资料` |

### 5.4 创建 Endpoint Revision

切换到 `Endpoint` 页签：

| 字段 | 填写值 |
| --- | --- |
| ProviderContract | `会员资料查询协议` |
| Endpoint 编码 | `local.member-center.member-query` |
| 环境 | `test` |
| 协议 | `HTTP` |
| Method | `POST` |
| Base URL | `http://127.0.0.1:19090` |
| 资源路径 | `/vendor/v1/members/query` |
| CredentialRef | `local.member-center.api-key · test` |
| 连接超时 | `1000` ms |
| 读取超时 | `2000` ms |
| 总超时 | `3000` ms |

创建后执行：

1. 点击 `探测`，确认能连接本地 Mock Provider；
2. 点击 `发布`，Endpoint 状态变成 `PUBLISHED`。

## 6. 第三步：配置第三方报文结构

进入：`集成配置 → 第三方报文结构`。

选择“会员资料查询协议”，点击 `＋ 新增 Version`：

| 字段 | 填写值 |
| --- | --- |
| 语义版本 | `1.0.0` |
| Error Schema | 留空 |
| Callback Schema | 留空 |

Request Schema：

```json
{
  "type": "object",
  "required": ["member_no"],
  "properties": {
    "member_no": { "type": "string" }
  },
  "additionalProperties": false
}
```

Response Schema：

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
        "member_no": { "type": "string" },
        "member_name": { "type": "string" },
        "mobile_no": { "type": "string" },
        "member_status": { "type": "string" }
      }
    }
  },
  "additionalProperties": false
}
```

Examples：

```json
{
  "request": {
    "member_no": "C1001"
  },
  "response": {
    "code": "0",
    "data": {
      "member_no": "C1001",
      "member_name": "张三",
      "mobile_no": "13800138000",
      "member_status": "ACTIVE"
    }
  }
}
```

创建后发布为 `PUBLISHED`。

## 7. 第四步：建立 Binding 与 JSONPath Mapping

进入：`集成配置 → JSONPath 字段映射`。

### 7.1 创建 Binding

在 `Binding` 页签点击 `＋ 新增 Binding`：

| 字段 | 填写值 |
| --- | --- |
| Binding 编码 | `customer.profile.lookup.local-member-center` |
| 名称 | `客户资料查询 - 本地会员中心适配` |
| Operation | `客户资料查询` |
| ProviderContract | `会员资料查询协议` |
| 负责人 | `integration-team` |

Binding 的含义是：“业务标准 Operation 由哪个第三方协议实现”。

### 7.2 创建出站 Mapping

在 `Mapping` 页签点击 `＋ 新增 Mapping`：

| 字段 | 填写值 |
| --- | --- |
| Binding | `客户资料查询 - 本地会员中心适配` |
| Mapping 编码 | `customer.profile.lookup.outbound-request` |
| 名称 | `客户查询请求转会员查询请求` |
| 方向 | `OUTBOUND_REQUEST` |

进入 `MappingVersion` 页签，选择该 Mapping，点击 `＋ 新增 Version`。Schema Reference 由页面根据 Binding 和方向自动生成。

添加一条规则：

| 字段 | 填写值 |
| --- | --- |
| Rule Code | `customer-id-to-member-no` |
| Order | `10` |
| Source JSONPath | `$.customerId` |
| Target JSONPath | `$.member_no` |
| Target Type | `STRING` |
| 必填 | 开启 |
| 缺失策略 | `FAIL` |
| 错误策略 | `FAIL` |

创建 DRAFT 后点击 `测试`，测试输入：

```json
{
  "customerId": "C1001"
}
```

期望输出：

```json
{
  "member_no": "C1001"
}
```

确认 `successful = true` 后发布该版本。

### 7.3 创建入站 Mapping

创建第二个 Mapping：

| 字段 | 填写值 |
| --- | --- |
| Binding | `客户资料查询 - 本地会员中心适配` |
| Mapping 编码 | `customer.profile.lookup.inbound-response` |
| 名称 | `会员查询响应转客户资料响应` |
| 方向 | `INBOUND_RESPONSE` |

创建 MappingVersion，并依次添加四条规则：

| Order | Rule Code | Source JSONPath | Target JSONPath | Target Type |
| --- | --- | --- | --- | --- |
| 10 | `member-no-to-customer-id` | `$.data.member_no` | `$.customerId` | `STRING` |
| 20 | `member-name-to-customer-name` | `$.data.member_name` | `$.customerName` | `STRING` |
| 30 | `mobile-no-to-mobile` | `$.data.mobile_no` | `$.mobile` | `STRING` |
| 40 | `member-status-to-status` | `$.data.member_status` | `$.status` | `STRING` |

四条规则都设置：

```text
必填 = 开启
缺失策略 = FAIL
错误策略 = FAIL
```

测试输入：

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

期望输出是本手册第 1 节的 Canonical Response。测试通过后发布。

## 8. 第五步：配置受控 Policy DSL

进入：`集成配置 → Policy DSL`。

### 8.1 创建 Policy

在 `Policy` 页签点击 `＋ 新增 Policy`：

| 字段 | 填写值 |
| --- | --- |
| Binding | `客户资料查询 - 本地会员中心适配` |
| Policy 编码 | `customer.profile.lookup.transport-policy` |
| 名称 | `客户查询传输与认证策略` |

### 8.2 创建 PolicyVersion

在 `PolicyVersion` 页签选择该 Policy，点击 `＋ 新增 DSL Version`，填写：

```json
{
  "apiVersion": "tpip.policy/v1alpha1",
  "kind": "PolicyChain",
  "stages": {
    "AFTER_REQUEST_MAPPING": [
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
    ],
    "BEFORE_TRANSPORT": [
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
    ]
  }
}
```

点击“编译并创建 DRAFT”。成功标志：

- Compile Status 为 `COMPILED`；
- 可以查看 Compiled Plan；
- Plan 中不包含 Secret 明文；
- 发布后 Lifecycle Status 为 `PUBLISHED`。

## 9. 第六步：冻结 BindingVersion

进入：`集成配置 → BindingVersion`。

选择“客户资料查询 - 本地会员中心适配”，点击 `＋ 冻结新版本`。

依次选择：

| 依赖 | 选择内容 |
| --- | --- |
| Canonical Request | `客户资料查询请求` 的 `1.0.0 PUBLISHED` |
| Canonical Response | `客户资料查询响应` 的 `1.0.0 PUBLISHED` |
| ProviderContract Published Version | `会员资料查询协议` 的 `1.0.0 PUBLISHED` |
| Endpoint | `local.member-center.member-query · test · PUBLISHED` |
| Outbound Mapping | `客户查询请求转会员查询请求` 的发布版本 |
| Inbound Mapping | `会员查询响应转客户资料响应` 的发布版本 |
| Policy | `客户查询传输与认证策略` 的 COMPILED + PUBLISHED 版本 |

Compliance Metadata：

```json
{
  "purpose": "manual-learning",
  "dataClassification": "INTERNAL"
}
```

Routing Attributes：

```json
{}
```

点击“校验并冻结 DRAFT”，查看 Bundle Manifest 预览，确认 Endpoint、两组 Mapping、Policy 和 Secret Reference 都正确，
然后发布 BindingVersion。

## 10. 第七步：创建 FixtureSuite

进入：`集成配置 → FixtureSuite`。

选择当前 Binding，点击 `＋ 新建 Suite`：

| 字段 | 填写值 |
| --- | --- |
| Suite Code | `customer.profile.lookup.fixtures` |
| Suite Name | `客户资料查询验收用例` |
| 说明 | `验证请求和响应 JSONPath Mapping` |

选择新建的 Suite，点击 `＋ 创建版本`。

### 10.1 Case 1：请求映射

| 字段 | 填写值 |
| --- | --- |
| Case Code | `outbound.success` |
| Case Name | `标准客户请求转换为会员请求` |
| Execution Mode | `MAPPING` |
| Direction | `OUTBOUND_REQUEST` |
| Expected Success | 开启 |

Source：

```json
{
  "customerId": "C1001"
}
```

Expected：

```json
{
  "member_no": "C1001"
}
```

FAP Assertions：

```json
[
  {
    "code": "mapping-success",
    "type": "SUCCESS",
    "expected": true
  },
  {
    "code": "member-number",
    "type": "JSON_PATH",
    "path": "$.member_no",
    "operator": "EQUALS",
    "expected": "C1001"
  }
]
```

### 10.2 Case 2：响应映射

| 字段 | 填写值 |
| --- | --- |
| Case Code | `inbound.success` |
| Case Name | `会员响应转换为标准客户资料` |
| Execution Mode | `MAPPING` |
| Direction | `INBOUND_RESPONSE` |
| Expected Success | 开启 |

Source：

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

Expected：

```json
{
  "customerId": "C1001",
  "customerName": "张三",
  "mobile": "13800138000",
  "status": "ACTIVE"
}
```

FAP Assertions：

```json
[
  {
    "code": "mapping-success",
    "type": "SUCCESS",
    "expected": true
  },
  {
    "code": "customer-id",
    "type": "JSON_PATH",
    "path": "$.customerId",
    "operator": "EQUALS",
    "expected": "C1001"
  },
  {
    "code": "active-status",
    "type": "POLICY_EXPRESSION",
    "expression": "$.status == \"ACTIVE\""
  }
]
```

创建 DRAFT 后发布 FixtureSuiteVersion。

本教程使用 `MAPPING` Fixture，避免为了学习基本流程而开放 REMOTE_CALL 白名单。真实项目再按安全要求增加远程用例。

## 11. 第八步：Workspace 服务端验证

进入：`集成配置 → Workspace 验证`。

### 11.1 创建 Workspace

点击 `＋ 新建 Workspace`：

| 字段 | 填写值 |
| --- | --- |
| Workspace Code | `customer.profile.lookup.release-001` |
| Workspace Name | `客户资料查询首次发布` |
| Environment | `test` |
| Risk Level | `LOW` |
| Owner Code | `integration-owner` |

### 11.2 装配与验证

1. 在“装配 BindingVersion”中选择刚才发布的版本；
2. 点击“装配不可变版本”；
3. 选择“客户资料查询验收用例”的 PUBLISHED Version；
4. 点击“执行服务端验证”；
5. 展开 Verification Checks 查看证据。

成功标志：

- Verification Job 状态为 `PASSED`；
- Failed Count 为 `0`；
- Workspace 生命周期从 `DRAFT` 变成 `VERIFIED`。

如果失败，不要重复创建 Workspace。查看失败 Check，修正对应资产并创建新 Version，然后回到当前 Workspace 重试。

## 12. 第九步：评审与发布 Bundle

进入：`集成配置 → 评审与 Bundle`。

选择“客户资料查询首次发布”。

### 12.1 提交评审

确认页面显示最近一次 `SERVER PASSED` 证据，然后点击 `Submit Review`。Workspace 进入 `IN_REVIEW`。

### 12.2 RELEASE 批准

本实例 Risk Level 是 LOW，只需要 RELEASE 批准：

| 字段 | 填写值 |
| --- | --- |
| Approval Stage | `RELEASE` |
| Decision | `APPROVED` |
| Approver Code | `release-reviewer` |
| Decision Comment | `已核对客户资料查询的 Mapping、Fixture 和服务端验证证据。` |

Approver Code 不能与 Workspace Owner `integration-owner` 相同。

### 12.3 编译并发布 Bundle

点击“编译 Bundle”：

| 字段 | 填写值 |
| --- | --- |
| Bundle Code | `customer.profile.lookup.local-member-center` |
| Semantic Version | `1.0.0` |

编译后状态为 `READY`。查看 Manifest，确认：

- Operation 是 `customer.profile.lookup`；
- Environment 是 `test`；
- Endpoint 指向 `127.0.0.1:19090`；
- 包含两组 Mapping Plan；
- 包含 Policy Plan；
- Secret 只有 Reference；
- Signature 状态明确为 `UNSIGNED`。

最后点击“发布”，Bundle 变成 `PUBLISHED`。

## 13. 第十步：Deployment 预热与激活

进入：`集成配置 → Deployment`。

选择 Workspace 和刚发布的 Bundle，点击 `＋ 创建 Deployment`：

| 字段 | 填写值 |
| --- | --- |
| Deployment Code | `customer.profile.lookup.test-v1` |
| Rollout Metadata | 见下方 JSON |

```json
{
  "strategy": "MANUAL_TUTORIAL",
  "operator": "local-user"
}
```

依次执行：

1. 创建后状态为 `PENDING`；
2. 点击 `预热`；
3. 确认状态变为 `READY`，证据中 Secret Reference、Endpoint、Schema Profile 和 Policy Provider 检查通过；
4. 点击 `激活`；
5. 因为这是该 Operation 在 test 环境的第一个 Deployment，Initial Traffic 必须是 `100%`；
6. 激活后状态为 `ACTIVE`，Active Route 显示一个 100% Target。

如果预热提示 Secret 不可用，检查 Runtime 是否在启动前设置了：

```text
TPIP_SECRET_E2E_PROVIDER_API_KEY=local-e2e-key
```

## 14. 第十一步：Runtime 调用验证

进入：`集成配置 → Runtime 调用`。

| 字段 | 填写值 |
| --- | --- |
| Operation | `客户资料查询 · customer.profile.lookup` |
| Runtime Environment | `test` |
| Request ID | 点击“生成 Request ID” |
| Caller | `manual-tutorial` |
| Tenant ID | 留空 |
| Idempotency Key | 留空 |
| Deadline | 留空 |

Attributes：

```json
{
  "source": "manual-tutorial"
}
```

Canonical Payload：

```json
{
  "customerId": "C1001"
}
```

点击“调用 Runtime”。成功时同时确认：

```text
HTTP Status = 200
result.success = true
result.code = SUCCESS
```

Payload 应为：

```json
{
  "customerId": "C1001",
  "customerName": "张三",
  "mobile": "13800138000",
  "status": "ACTIVE"
}
```

HTTP 200 只表示 Runtime 接收并完成处理，仍必须检查 `result.success` 和 `result.code`。

## 15. 最终检查

回到：`集成配置 → 接入向导`。

选择：

```text
Operation = customer.profile.lookup
Provider Contract = local.member-center.member-query
Environment = test
Binding = customer.profile.lookup.local-member-center
Workspace = customer.profile.lookup.release-001
```

八个阶段应全部显示“已完成”，总体进度应为 `100%`。

## 16. 常见问题

| 现象 | 原因与处理 |
| --- | --- |
| 页面显示 `CONTROL OFFLINE` | Control Plane 实际端口与 Vite 代理端口不一致；当前 UI 默认使用 18082 |
| 创建时提示编码冲突 | 稳定资产已经存在，应复用；练习发布可新建 `release-002`、Bundle `1.0.1` 或 Deployment `test-v2` |
| MappingVersion 没有可选 Schema | Canonical 或 ProviderContract Version 尚未发布，或 Mapping Direction 选错 |
| BindingVersion 没有可选依赖 | Endpoint、两组 MappingVersion 或 PolicyVersion 尚未发布 |
| Workspace 找不到 BindingVersion | BindingVersion 未发布，或者 Endpoint 环境与 Workspace 环境不一致 |
| Workspace 验证失败 | 展开失败 Check；确认 Mock Provider 已启动、Fixture 属于当前 Binding、Endpoint 为 test |
| 审批按钮不可用 | Workspace 尚未 VERIFIED/IN_REVIEW，或者缺少 PASSED Verification Job |
| 审批被拒绝 | Approver 与 Workspace Owner 相同，或 Row Version 已变化；刷新后重新操作 |
| Deployment 预热失败 | Runtime 未启动、Secret 环境变量未在启动前注入、Bundle checksum/兼容性检查失败 |
| Runtime 返回 503 | 当前 Operation + Environment 没有 ACTIVE Route，或 Bundle 无法加载 |
| Runtime 返回 200 但业务失败 | 查看 `result.code`，不要只判断 HTTP Status |

## 17. 哪些值在真实项目中需要替换

本教程中可以原样使用的只有学习场景。真实接入时必须替换：

- Provider 名称和负责人；
- 第三方请求、响应和错误 Schema；
- Endpoint 地址、资源路径、Method 和超时；
- Secret Reference；
- JSONPath Mapping；
- Policy 类型和参数；
- Fixture 数据与断言；
- Workspace 风险等级和审批人；
- Runtime 调用的业务 Payload。

不应因更换第三方而修改稳定业务语义 `operationCode`；只有业务能力本身发生变化时，才演进 Canonical Contract。

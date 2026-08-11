# TPIP MCP Tool资产配置与发布指南 v0.2

## 1. 目标

MCP Tool不再写在MCP Server本地YAML中，而是作为Control Plane正式资产管理。一个Tool由稳定主资产和不可变版本组成：

```text
业务标准服务（serviceCode）
  └── MCP Tool主资产（稳定名称和归属）
        ├── v1 草稿 -> 验证 -> 发布
        ├── v2 草稿 -> 验证 -> 发布
        └── 历史发布版本只读保留
```

同一个业务标准服务可以按不同业务场景暴露多个Tool，例如“发送业务短信”和“发送登录验证码”；它们可以使用不同的 `fixedScenario`、说明和风险提示，但仍复用同一个TPIP路由与Runtime能力。

## 2. 数据模型

Flyway `V54__mcp_tool_assets.sql` 新增：

| 表 | 作用 |
| --- | --- |
| `tpip_mcp_tool` | Tool稳定身份，保存业务标准服务、协议名称、中文名称、负责人和启用状态 |
| `tpip_mcp_tool_version` | 不可变版本，保存说明、输入输出Schema、固定场景、风险提示、校验值和发布证据 |

发布新版本不会修改或删除旧版本。Runtime快照只输出版本号最大的已发布版本，旧版本用于审计和回溯。

## 3. 页面配置流程

打开 TPIP UI，进入“服务管理 → AI 工具开放”（路由 `/integration-assets/ai-tools`）。页面采用四步业务向导：

1. 选择已经稳定运行的业务标准服务，填写用户看到的工具名称、稳定调用编码、用途边界和负责人；
2. 平台自动读取该服务已发布的标准请求与标准返回契约，并转换成中文业务字段表单供确认；
3. 设置只读、可能产生外部影响、可安全重试、会访问外部系统以及调用前确认方式；
4. 保存草稿，执行平台校验，确认无阻断问题后发布。

列表页展示最新版本和最新已发布版本，两者不同时表示“线上仍使用旧发布版本，同时存在一个新草稿”。已发布版本只读，继续调整必须点击“创建新版本”。AI 只看到业务工具，不会看到具体第三方厂商或通道。

工具详情中的“检查契约变化”会把指定工具版本与业务服务最新已发布的标准请求、标准返回契约比较：

| 结果 | 含义 | 建议动作 |
| --- | --- | --- |
| 与最新业务契约一致 | 字段、类型和必填约束没有变化 | 无需处理 |
| 发现可兼容新增 | 新增选填字段、返回字段或放宽必填约束 | 可按需要创建新版本同步 |
| 存在不兼容变化 | 删除已有字段、改变字段类型或新增请求必填字段 | 创建工具新版本并重新验证发布 |
| 暂时无法分析 | 缺少已发布请求/返回契约 | 先完善业务标准契约 |

分析只读取已发布 Canonical Contract 和不可变 Tool Version，不读取第三方原始报文、Secret、设计态 Mapping 或 Policy。

## 4. API配置流程

### 第一步：选择业务标准服务并创建Tool

请求中的 `operationId` 来自业务标准服务，不是第三方接口或接入通道ID。

```http
POST /control/v1/mcp-tools
X-Operator: sean
Content-Type: application/json

{
  "operationId": 100,
  "toolName": "send_business_sms",
  "displayName": "发送业务短信",
  "description": "由TPIP按照路由规则选择可用短信通道并发送业务短信",
  "ownerCode": "integration-owner"
}
```

`toolName` 是MCP协议稳定名称，只允许字母、数字、下划线和短横线；`displayName` 和 `description` 面向业务用户与AI，必须使用容易理解的业务语言。

### 第二步：创建不可变草稿版本

```http
POST /control/v1/mcp-tools/{toolId}/versions
X-Operator: sean
Content-Type: application/json

{
  "title": "发送业务短信",
  "description": "向指定手机号发送验证码或业务通知；具体厂商由平台路由决定",
  "fixedScenario": "verification-code",
  "inputSchema": {
    "type": "object",
    "additionalProperties": false,
    "required": ["mobile", "templateCode", "parameters"],
    "properties": {
      "mobile": {"type": "string", "description": "接收手机号"},
      "templateCode": {"type": "string", "description": "业务模板编码"},
      "parameters": {"type": "object", "description": "模板变量"}
    }
  },
  "outputSchema": {
    "type": "object",
    "properties": {
      "messageId": {"type": "string", "description": "发送流水号"},
      "status": {"type": "string", "description": "受理状态"}
    }
  },
  "readOnly": false,
  "destructive": false,
  "idempotent": false,
  "openWorld": true,
  "confirmationMode": "NONE"
}
```

输入和返回必须是 `type=object` 的业务标准JSON Schema，不能复制阿里云、腾讯云或华为云原始报文。平台会规范化字段顺序并生成SHA-256内容校验值。

### 第三步：验证版本

```http
POST /control/v1/mcp-tools/{toolId}/versions/{versionId}:validate
```

返回示例：

```json
{"ready": true, "issues": []}
```

验证至少检查：Tool和业务标准服务是否启用、输入输出是否为对象Schema、只读与破坏性提示是否冲突，以及破坏性Tool是否要求确认。

### 第四步：发布版本

```http
POST /control/v1/mcp-tools/{toolId}/versions/{versionId}:publish
X-Operator: sean
```

只有草稿且验证通过的版本可以发布。已发布版本不可再次发布或原地修改；变更必须创建新版本。

### 第五步：检查发布快照

```http
GET /control/v1/mcp-tools/runtime-snapshot
```

快照版本为 `tpip.mcp-tools/v1`，包含每个启用Tool的最新已发布版本。MCP Server启动时读取该快照，再与本地应用的已发布服务授权取交集，最终形成 `tools/list`。运行中默认每30秒原子刷新，也可以从“本地调用测试”手动刷新；成功后向已连接客户端发送 `tools/list_changed` 通知。

## 5. 查询接口

| 接口 | 用途 |
| --- | --- |
| `GET /control/v1/mcp-tools` | Tool列表和最新版本摘要 |
| `GET /control/v1/mcp-tools/{toolId}` | Tool详情与完整版本历史 |
| `GET /control/v1/mcp-tools/{toolId}/versions/{versionId}/contract-impact` | 指定不可变版本与最新业务契约的兼容性分析 |
| `GET /control/v1/mcp-tools/runtime-snapshot` | MCP Server只读发布快照 |

## 6. 风险字段解释

| 字段 | 含义 |
| --- | --- |
| `readOnly` | 只读取信息，不改变外部系统状态 |
| `destructive` | 可能删除、覆盖或产生难以恢复的外部影响 |
| `idempotent` | 使用相同输入重复调用是否具有相同业务效果 |
| `openWorld` | 是否会访问TPIP之外的第三方系统 |
| `confirmationMode` | `NONE`无需确认，`REQUIRED`按风险确认，`ALWAYS`每次确认 |

这些字段首先用于向MCP Client和AI说明风险，不能替代TPIP服务授权、场景限制、配额、路由和Runtime治理。

## 7. 当前边界

- 中文业务化工作台已经完成，支持查询、创建、契约字段确认、风险设置、验证、发布和不可变版本历史。
- Tool快照和授权交集支持运行中自动/手动刷新；刷新失败保留上一份成功快照，无需重启MCP Server。
- 当前没有Tool下线命令，紧急阻断仍可通过撤销调用方服务授权完成。
- 工作台已从已发布 Canonical Contract 自动生成字段初稿，并提供契约变化影响分析、本地客户端配置参考和真实调用测试台。

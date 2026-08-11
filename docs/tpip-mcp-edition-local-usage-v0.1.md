# TPIP MCP Edition 本地使用指南 v0.1

## 1. 适用范围

本指南用于在本机把已经发布并授权的 TPIP 业务标准服务，通过 MCP Streamable HTTP 暴露给受控的 MCP Client。当前版本只适合单人本地验证：MCP Server绑定 `127.0.0.1`，使用一个固定的 `ConsumerApplication` 身份，不包含远程OAuth/OIDC认证。

MCP不是绕过TPIP直接调用第三方厂商。完整链路仍然是：

```text
MCP Client
  -> TPIP MCP Server /mcp
  -> serviceCode与应用服务授权校验
  -> TPIP Runtime /integration/v1/operations/{serviceCode}:invoke
  -> 已发布Bundle、路由、Mapping、Policy和第三方接口
```

## 2. 启用前准备

需要先在TPIP平台完成以下内容：

1. 业务标准服务已经发布，并获得稳定的 `serviceCode`，例如 `notification.sms.send`。
2. 至少一个第三方通道和执行方案已经发布，Runtime能够正常调用。
3. 已创建调用方应用，获得 `applicationId`、`applicationCode`、`appKey` 和Secret。
4. 调用方应用已经获得该 `serviceCode` 的已发布服务授权。
5. Control Plane和Runtime均已启动，默认示例地址分别为 `http://127.0.0.1:18082` 和 `http://127.0.0.1:18081`。

如果服务未授权，Tool即使写入配置也不会出现在 `tools/list` 中；这是预期的安全收敛行为。

## 3. 编写本地配置

在工程外创建本地文件，例如 `/Users/your-name/.tpip/tpip-mcp-local.yml`。不要把真实 `appKey`、Secret或本地配置文件提交到Git。

```yaml
tpip:
  mcp:
    enabled: true
    runtime-base-uri: http://127.0.0.1:18081
    control-plane-base-uri: http://127.0.0.1:18082
    local-identity:
      application-id: 1001
      application-code: local-ai-assistant
      tenant-id: local
      app-key: tpip_replace_with_real_app_key
      secret-reference: env://TPIP_MCP_LOCAL_APP_SECRET
    tools:
      - tool-id: 1
        name: send_business_sms
        title: 发送业务短信
        description: 按平台路由规则选择已发布的短信通道并发送业务短信
        service-code: notification.sms.send
        fixed-scenario: verification-code
        version-no: 1
        read-only: false
        destructive: false
        idempotent: false
        open-world: true
        confirmation-mode: NONE
        input-schema: >-
          {"type":"object","additionalProperties":false,"required":["mobile","templateCode","parameters"],"properties":{"mobile":{"type":"string","description":"接收短信的手机号"},"templateCode":{"type":"string","description":"业务短信模板编码"},"parameters":{"type":"object","description":"模板变量"}}}
        output-schema: >-
          {"type":"object","properties":{"messageId":{"type":"string","description":"短信发送流水号"},"status":{"type":"string","description":"发送受理状态"}}}
```

字段含义：

| 字段 | 业务含义 |
| --- | --- |
| `name` | MCP Client实际调用的稳定工具名，建议使用英文小写和下划线 |
| `title` | 用户看到的中文名称 |
| `description` | 告诉AI和使用者该工具能做什么、不能做什么 |
| `service-code` | TPIP业务标准服务编码，不是阿里云、腾讯云或华为云接口编码 |
| `fixed-scenario` | 可选；强制使用固定业务场景，调用方不能覆盖 |
| `input-schema` | 业务标准请求结构，不是第三方原始报文 |
| `output-schema` | 业务标准返回结构，不是第三方原始报文 |
| 风险提示字段 | 提示MCP Client是否只读、幂等、破坏性或访问外部系统；服务端授权仍是最终约束 |

## 4. 启动 MCP Server

先将应用Secret放入环境变量，Secret Reference只保存引用，不保存明文：

```bash
export TPIP_MCP_LOCAL_APP_SECRET='替换为调用方应用的真实Secret'
```

构建并启动：

```bash
mvn -pl tpip-mcp-server-app -am package
java -jar tpip-mcp-server-app/target/tpip-mcp-server-app-*.jar \
  --spring.config.additional-location=file:/Users/your-name/.tpip/tpip-mcp-local.yml
```

默认监听地址是 `http://127.0.0.1:18083`，MCP端点是：

```text
http://127.0.0.1:18083/mcp
```

`TPIP_MCP_ENABLED` 默认是 `false`。没有显式启用、身份字段不完整、Control Plane不可访问或Secret Reference无法解析时，服务会拒绝建立可调用的MCP入口，而不是降级成无授权调用。

## 5. MCP Client配置

在支持Streamable HTTP的MCP Client中增加一个服务器，URL填写：

```text
http://127.0.0.1:18083/mcp
```

本地版本不要求MCP Client配置TPIP的 `appKey` 或Secret。身份和签名由本机MCP Server完成，避免把平台凭证散落到多个客户端。

连接成功后应当能够：

1. 初始化MCP会话；
2. 在工具列表中看到“发送业务短信”；
3. 按 `input-schema` 提交手机号、模板编码和模板变量；
4. 得到标准业务结果以及 `tpip/requestId`；
5. 使用同一个requestId在TPIP Runtime审计中追踪路由和第三方调用。

## 6. 常见问题

### 配置了Tool但客户端看不到

优先检查该应用是否已经获得同一 `serviceCode` 的已发布授权，其次检查Tool名称、Schema和Control Plane地址。当前版本在启动时形成Tool快照，新增Tool、发布授权或撤销授权后需要重启MCP Server。

### 为什么Tool不直接对应阿里云短信接口

MCP Tool对应业务能力 `notification.sms.send`。阿里云、腾讯云、华为云属于业务能力下面的第三方通道，由TPIP路由策略选择。这样AI不会绑定具体厂商，通道故障切换或权重调整也不需要修改Tool。

### 能否开放给其他机器使用

当前版本不能。它只绑定本机回环地址，固定身份也不适合多人共享。远程使用必须先增加TLS、OAuth2/OIDC、客户端身份映射、配额和更严格的网络边界。

## 7. 当前版本边界与下一步

当前Tool定义保存在本地YAML，只是为了先验证MCP协议、授权交集和Runtime复用是否成立。下一阶段将把 `MCP Tool Asset` 和不可变版本写入Control Plane，并提供中文业务化UI，配置流程计划为“选择业务标准服务—填写工具说明—确认标准请求/返回—配置风险提示—验证—发布”，用户不再手写YAML或JSON Schema。

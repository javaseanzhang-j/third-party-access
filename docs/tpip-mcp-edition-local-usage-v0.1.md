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

如果服务未授权，Tool即使已经发布也不会出现在 `tools/list` 中；这是预期的安全收敛行为。Tool资产配置和发布流程见 [MCP Tool资产配置与发布指南 v0.2](./tpip-mcp-tool-asset-configuration-v0.2.md)。

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
```

MCP Server默认从Control Plane读取最新已发布Tool快照，本地文件不再维护Tool定义。`configured-tools-enabled` 默认关闭，只有自动化测试和迁移排障才允许使用静态Tool配置。

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

优先检查Tool版本是否已经发布，再检查该应用是否已经获得同一 `serviceCode` 的已发布授权，最后检查Control Plane地址。当前版本在启动时形成Tool快照，发布Tool、发布授权或撤销授权后需要重启MCP Server。

### 为什么Tool不直接对应阿里云短信接口

MCP Tool对应业务能力 `notification.sms.send`。阿里云、腾讯云、华为云属于业务能力下面的第三方通道，由TPIP路由策略选择。这样AI不会绑定具体厂商，通道故障切换或权重调整也不需要修改Tool。

### 能否开放给其他机器使用

当前版本不能。它只绑定本机回环地址，固定身份也不适合多人共享。远程使用必须先增加TLS、OAuth2/OIDC、客户端身份映射、配额和更严格的网络边界。

## 7. 当前版本边界与下一步

当前Tool定义已经作为 `MCP Tool Asset` 和不可变版本写入Control Plane，并通过“创建Tool—创建版本—验证—发布”流程管理。下一步提供中文业务化UI，将JSON Schema编辑转换为字段表单和契约预览，并增加变更影响提示；在UI完成前可按资产配置指南调用Control Plane API。

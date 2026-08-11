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
    catalog-auto-refresh-enabled: true
    catalog-refresh-interval: 30s
    local-identity:
      application-id: 1001
      application-code: local-ai-assistant
      tenant-id: local
      app-key: tpip_replace_with_real_app_key
      secret-reference: env://TPIP_MCP_LOCAL_APP_SECRET
```

MCP Server默认从Control Plane读取最新已发布Tool快照，本地文件不再维护Tool定义。运行中默认每30秒原子刷新一次“已发布工具与当前应用授权”的交集；刷新失败会继续保留上一份可用快照。`configured-tools-enabled` 默认关闭，只有自动化测试和迁移排障才允许使用静态Tool配置。

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

也可以在 TPIP UI 打开“服务管理 → AI 工具开放”，点击右上角“客户端接入”，复制本机地址和通用 Streamable HTTP 配置参考。该入口同时提示完整前置顺序：发布工具、授权业务服务、启动 MCP 服务。

同一页面的“本地调用测试”用于正式连接客户端前联调：

1. 查看当前本地应用身份、目录更新时间和可调用工具数量；
2. 点击“立即刷新目录”，无需重启MCP Server即可吸收新发布Tool或授权变化；
3. 选择当前应用真正有权调用的工具，平台按输入Schema生成参数示例；
4. 检查并修改参数，必要时填写业务场景和幂等键；
5. 确认后发起真实调用并查看标准结果和requestId。

这不是Mock或Dry Run。调用仍经过服务授权、Runtime、已发布Bundle、路由、Mapping和Policy，并可能真实访问第三方。该测试入口额外校验请求来源，只允许本机回环地址访问。

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

优先检查Tool版本是否已经发布，再检查该应用是否已经获得同一 `serviceCode` 的已发布授权，最后检查Control Plane地址。在“AI 工具开放”中点击“本地调用测试 → 立即刷新目录”，或等待默认30秒自动刷新；不需要重启MCP Server。若刷新失败，页面会显示失败时间和原因，同时上一份成功快照继续可用。

### 为什么Tool不直接对应阿里云短信接口

MCP Tool对应业务能力 `notification.sms.send`。阿里云、腾讯云、华为云属于业务能力下面的第三方通道，由TPIP路由策略选择。这样AI不会绑定具体厂商，通道故障切换或权重调整也不需要修改Tool。

### 能否开放给其他机器使用

当前版本不能。它只绑定本机回环地址，固定身份也不适合多人共享。远程使用必须先增加TLS、OAuth2/OIDC、客户端身份映射、配额和更严格的网络边界。

## 7. 当前版本边界与下一步

当前Tool定义已经作为 `MCP Tool Asset` 和不可变版本写入Control Plane，并可在“服务管理 → AI 工具开放”通过中文四步向导完成创建、业务字段确认、风险设置、验证和发布。Tool与授权支持运行中自动/手动刷新和MCP `tools/list_changed` 通知，本地工作台支持授权发现与真实调用验证；远程OAuth/OIDC客户端尚未开放。

可用环境变量：`TPIP_MCP_CATALOG_AUTO_REFRESH_ENABLED` 控制自动刷新，默认 `true`；`TPIP_MCP_CATALOG_REFRESH_INTERVAL` 控制周期，默认 `30s`。

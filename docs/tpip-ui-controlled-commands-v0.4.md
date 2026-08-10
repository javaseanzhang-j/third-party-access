# TPIP 治理工作台受控写操作 v0.4

## 已接入操作

- 调整任务优先级；
- 取消任务；
- 重试失败 Workspace；
- 封板全局影响快照。

UI 仍以服务端 `allowedActions` 为第一层能力提示，命令端点保留最终状态、过期、版本和证据门禁。

## 交互流程

```text
读取详情与 allowedActions
  → 打开确认对话框
  → 填写原因/选择优先级
  → 携带 rowVersion + X-Operator 提交
  → 成功：关闭对话框并刷新全部查询
  → 409：不重放，关闭对话框，刷新并提示重新确认
  → 400/其他错误：保留对话框和错误信息
```

封板不要求填写一个服务端不会保存的“假原因”，而是显示不可逆警告并要求显式确认。取消、重试和优先级调整的原因进入现有审计记录。

## HTTP Client

- 新增通用 `postJson`；
- `ApiError` 解析 `status`、`code`、`message`、`details`；
- JSON POST 自动设置 Content-Type；
- 请求增强器仍在所有 GET/POST 请求之前执行，为未来认证保留统一入口；
- 本地审计操作者默认为 `local-ui`，可通过非敏感的 `VITE_TPIP_OPERATOR` 覆盖；
- 不把 Operator 冒充登录身份，不生成伪 JWT。

## 安全与一致性

- 写请求不自动重试；
- 每次命令使用打开详情时的 rowVersion；
- 提交期间禁用其他操作；
- 成功后刷新详情、全量摘要、Workspace 明细和审计时间线；
- 服务端冲突统一为 `TPIP_GLOBAL_IMPACT_JOB_COMMAND_CONFLICT`；
- UI 不保存数据库密码、Token 或第三方 Secret；
- 无登录模式仅允许 loopback 单用户运行。

## 暂未纳入

- 封板快照资产详情页；
- 批量任务命令；
- OIDC/JWT/RBAC；
- Playwright 真实浏览器执行。

## 下一步

UI v0.5 建设封板快照只读资产页、操作结果审计定位和命令组件测试；真实成功写操作验收使用专门创建的可丢弃 Fixture，不复用历史资产。

# Verification Global 影响任务命令冲突契约 v1.6

## 问题

全局影响任务的状态门禁、过期门禁和 rowVersion 并发冲突过去均使用 `IllegalArgumentException`，最终映射为 HTTP 400。调用方无法区分输入校验失败与资源状态已经变化，也无法可靠决定是否刷新。

## 标准契约

以下命令在资源状态、过期时间、覆盖范围、证据完整性或 rowVersion 不再满足执行前提时，统一返回：

```json
{
  "code": "TPIP_GLOBAL_IMPACT_JOB_COMMAND_CONFLICT",
  "message": "...",
  "details": { "refreshRequired": true }
}
```

HTTP Status 为 `409 Conflict`。

适用命令：

- `:reprioritize`；
- `:cancel`；
- `:retry-failed`；
- `:seal`。

## 客户端规则

- 每次提交必须携带查询模型返回的当前 `rowVersion`；
- 冲突后必须刷新，不得自动重放写请求；
- 刷新后重新读取 `allowedActions`；
- 用户必须基于最新状态再次显式确认；
- HTTP 400 仍表示请求结构、枚举、原因或 Header 校验失败。

## 服务端实现

- 领域层新增 `GlobalImpactJobCommandConflictException`；
- JDBC 条件更新影响 0 行时抛出命令冲突；
- 封板前的状态、rowVersion、Coverage 和 Workspace 证据检查同样使用该异常；
- 全局异常处理器统一生成 409 错误文档；
- 不新增数据库迁移，不改变成功响应和审计事件。

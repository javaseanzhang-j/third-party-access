# Verification Global 影响摘要查询模型 v1.5

## 决策

UI 风险分布必须来自服务端针对整个任务的聚合，禁止对 Workspace 分页结果做本地统计后标记为全局数据。

## 只读契约

```text
GET /control/v1/verification-drift-workbench/
    global-governance-policy-impact-job-views/{jobId}/impact-summary
```

响应包含：

- `workspaceCount`：任务冻结的 Workspace 总数；
- `risks`：LOW、MEDIUM、HIGH、CRITICAL 全量数量；
- `statuses`：PENDING、RUNNING、SUCCEEDED、FAILED 全量数量；
- `impactTotals`：成功快照中的变化报告、新增超期报告和新增提醒候选总量。

## 实现边界

- 查询端口仍与命令聚合仓储分离；
- JDBC 使用单次只读聚合查询，不加载全部明细到 JVM；
- 只有 `SUCCEEDED` 条目的不可变快照参与影响指标求和；
- 没有快照或 JSON 指标缺失时按 0 处理；
- 不新增数据库表或 Flyway 迁移；
- 不修改任务状态、rowVersion、快照和审计数据；
- 无登录模式不影响查询语义，未来认证由既有请求增强扩展点统一接入。

## 验证

- Service 测试证明返回的是 12 个 Workspace 的完整聚合，而不是一页明细；
- Controller 契约测试固定 `/impact-summary` 路由；
- Java 21 全量 Maven 回归通过；
- MySQL 8.4 本地真实数据执行通过，schema 仍为 V40。

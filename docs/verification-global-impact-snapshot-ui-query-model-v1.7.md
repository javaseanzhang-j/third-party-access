# Global 影响封板快照 UI 查询模型 v1.7

## 1. 目标

为 UI 提供稳定、只读的封板快照契约，避免前端依赖领域实体和原始 `impactDocument`。本版本不新增数据库表，
不改变快照创建、封板、发布消费和激活消费规则。

## 2. 边界

- 聚合快照继续是不可变治理证据，查询模型不提供写接口。
- 查询层只依赖领域仓储端口，不依赖 JDBC 实体或命令服务。
- 原始 JSON 仅在服务端解析，UI 只接收结构化参数变化与影响摘要。
- 原有治理 API 保留，稳定 View API 独立演进。

## 3. API

基础路径：

```text
/control/v1/verification-drift-workbench/global-governance-policy-impact-snapshot-views
```

资源：

```text
GET /{snapshotId}
GET /{snapshotId}/workspace-snapshots?page=0&size=20
```

快照详情包含候选策略身份、三类 checksum、Workspace 数量、有效期、创建审计和发布/激活消费证据。
Workspace 分页包含 Workspace 身份、风险等级、当前策略、子快照 checksum、结构化参数变化和影响统计。

## 4. 设计说明

实现入口：

- `GlobalImpactSnapshotQueryService`：读取聚合和子快照，补齐资产身份，解析证据 JSON。
- `GlobalImpactSnapshotQueryController`：暴露稳定只读 HTTP 契约。
- `GlobalImpactSnapshotQueryServiceTest`：验证不可变详情投影与 JSON 结构化。
- `GlobalImpactSnapshotQueryControllerContractTest`：锁定路由契约。

当前每页最多 100 条，按子快照补齐 Workspace/策略身份。进入共享环境或数据规模扩大后，应新增专用 JOIN 查询端口，
消除分页内的逐项仓储查询；该优化不得改变本 View API。

## 5. 验收结果

- Java 21 Maven Reactor：298 tests，0 failures，0 errors。
- 新增服务测试 2 项、控制器契约测试 1 项。
- 未创建或修改本地业务数据。


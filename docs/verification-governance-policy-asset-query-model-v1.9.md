# 治理策略资产 UI 查询模型 v1.9

## 1. 目标

为 UI 提供稳定、只读的治理策略资产查询契约，支持从策略列表追溯到不可变版本及其全局影响任务。该模型与现有
`/drift-governance-policies` 命令聚合接口分离，前端不直接拼装领域对象或数据库关系。

## 2. 边界

- 不新增数据库表或迁移；
- 不改变策略创建、版本创建、发布、激活和暂停命令；
- 不提供版本原地修改能力，已发布资产仍遵守不可变约束；
- 只返回治理参数、Workspace 身份、checksum 和任务使用关系，不返回 Secret 或原始 Provider 报文；
- 任务及快照处置继续使用既有稳定 View API 和受控命令。

## 3. API

基础路径：

```text
/control/v1/verification-drift-workbench/drift-governance-policy-asset-views
```

资源：

```text
GET ?scope=GLOBAL&status=ACTIVE&keyword=core&page=0&size=20
GET /{policyId}?recentJobLimit=20
GET /{policyId}/versions/{versionId}?recentJobLimit=20
```

列表支持作用域、状态、Workspace ID、关键字和分页。关键字匹配策略编码、名称、Workspace 编码、名称及环境。
每页限制 1～100 条。

## 4. 投影内容

策略资产包含：

- 策略身份、Global/Workspace 作用域和 Workspace 展示身份；
- 策略状态、当前版本、Row Version 和创建/更新时间；
- 版本总数、作为候选策略产生的影响任务数。

版本资产包含：

- 逾期阈值、聚合窗口、提醒间隔、最大提醒次数和负责人；
- 抑制漂移类型、抑制检查项、Content Checksum；
- 生命周期、是否为当前版本、发布/创建时间和影响任务数。

关联任务包含任务状态、优先级、Workspace 进度、封板快照身份和时间事实，最多返回最近 100 条。

## 5. 工程实现

- `DriftGovernancePolicyAssetQueryRepository`：领域侧只读投影端口；
- `JdbcDriftGovernancePolicyAssetQueryRepository`：分页、Workspace JOIN、版本/任务聚合及 JSON 解析；
- `DriftGovernancePolicyAssetQueryService`：稳定应用契约和任务进度计算；
- `DriftGovernancePolicyAssetQueryController`：独立 View API；
- 服务测试与控制器契约测试锁定分页、版本参数和路由。

领域模块不依赖 Spring、JDBC 或数据库 Entity。应用响应不暴露 JDBC 行模型。

## 6. 真实环境验收

连接本地 MySQL `tpip_platform` 完成只读验收：

- 查询到 3 个策略资产：2 个 Global、1 个 Workspace；
- Workspace 策略正确投影 Workspace 名称和 `test` 环境；
- policyId=3 正确返回 versionId=3、DRAFT 生命周期和完整治理参数；
- 版本正确关联既有 SEALED 任务 `632471a8-d9b7-41bb-b393-9cd224d70e19`；
- 任务进度为 10/10、100%，并关联快照 `d85a45f0-8474-4b2f-87bb-08923fbb0544`；
- Java 21 Maven Reactor：303 tests，0 failures，0 errors；
- 验收没有创建、修改或删除业务数据。

## 7. 后续演进

当策略或任务规模增大时，可将最近任务改为游标分页，并增加按 owner、环境和更新时间筛选；现有字段语义与资源路径
保持向后兼容。策略写入能力应继续由命令 API 承担，不合并进资产 View API。


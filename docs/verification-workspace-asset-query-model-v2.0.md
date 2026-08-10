# Workspace 资产 UI 查询模型 v2.0

## 1. 目标与边界

为 UI 提供稳定、只读的 Workspace 资产追溯契约，统一投影 Workspace 身份、有效治理策略、最新验证基线、漂移汇总、
基线谱系和全局影响证据。不新增数据库迁移，不改变 Workspace、策略、验证或漂移处置命令。

## 2. API

```text
GET /control/v1/verification-drift-workbench/workspace-asset-views
    ?lifecycleStatus=VERIFIED&riskLevel=HIGH&environmentCode=test&keyword=customer&page=0&size=20
GET /control/v1/verification-drift-workbench/workspace-asset-views/{workspaceId}?limit=20
```

列表支持生命周期、风险、环境、关键字和分页。详情最多返回最近 100 条基线、漂移报告和影响证据。

## 3. 解析规则

- 优先选择该 Workspace 的 ACTIVE Workspace 策略；
- 没有 Workspace 策略时回退到 ACTIVE Global 策略；
- 两者均不存在时返回空策略，表示使用系统默认治理参数；
- PAUSED/DRAFT 策略绝不作为有效策略展示；
- 最新基线按 Workspace 内最大基线 ID 确定；
- actionable 漂移仅统计 OPEN、ACKNOWLEDGED 评审；
- 影响证据从全局任务 Workspace Item 反向关联任务、候选策略、子快照和封板快照。

## 4. 实现与验收

- `WorkspaceAssetQueryRepository`：领域只读端口；
- `JdbcWorkspaceAssetQueryRepository`：策略解析、基线/漂移聚合与影响证据 JOIN；
- `WorkspaceAssetQueryService`：稳定应用响应；
- `WorkspaceAssetQueryController`：独立 View API；
- 新增 2 项服务测试和 1 项控制器契约测试。
- Java 21 Maven Reactor：306 tests，0 failures，0 errors。

真实 MySQL 验收：识别 10 个 Workspace；workspaceId=23 返回 2 代基线、1 条 ACCEPTED 漂移报告及 4 条影响任务证据，
其中包含任务 `632471a8-d9b7-41bb-b393-9cd224d70e19` 和封板快照
`d85a45f0-8474-4b2f-87bb-08923fbb0544`。当前无 ACTIVE 策略，投影正确返回系统默认。验收未修改业务数据。

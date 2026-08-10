# Verification Global 影响任务 UI 查询模型 v1.4

## 1. 目标与边界

v1.4 为后续 UI 提供稳定的只读查询契约，使前端不直接依赖领域聚合、数据库字段或内部 Worker API。

本阶段不创建前端工程，不加入登录、OIDC/JWT、RBAC、用户或角色管理，不新增命令行为，也不自动重试、取消、封板、发布或激活任何资产。查询继续遵守本地单用户安全边界。

## 2. 设计结构

```text
UI（下一阶段）
  -> GlobalImpactJobQueryController
  -> GlobalImpactJobQueryService / stable View DTO
  -> GlobalImpactJobQueryRepository（只读端口）
  -> JdbcGlobalImpactJobQueryRepository
  -> V38-V40 已有任务、Workspace、快照和审计表
```

读模型与命令聚合仓储分离。已有命令 API 保持不变，UI 不读取原始 `impact_document`，而是接收服务端解析后的结构化对比数据。

## 3. API

基础路径：

```text
/control/v1/verification-drift-workbench/global-governance-policy-impact-job-views
```

接口：

```text
GET /
GET /{jobId}
GET /{jobId}/workspace-impacts
GET /{jobId}/timeline
```

### 3.1 任务列表

支持过滤：

- `status`：可重复或逗号分隔的任务状态；
- `priority`：可重复或逗号分隔的优先级；
- `candidatePolicyId`；
- `createdBy`；
- `keyword`：匹配 jobId、策略编码或策略名称；
- `createdFrom`、`createdTo`：ISO-8601 时间，左闭右开；
- `page`、`size`：0 基页码，size 为 1～100。

固定排序为 `updatedAt DESC, jobId DESC`，避免翻页时出现不确定顺序。分页响应统一包含 `totalElements`、`totalPages` 和 `hasNext`。

列表项提供候选策略身份、任务状态和优先级、任务项计数、处理进度、过期事实、rowVersion、最后进度时间及 `allowedActions`。

### 3.2 任务详情

详情在列表摘要之外提供：候选与覆盖 checksum、快照时间、封板快照、调度次数、最近调度/进度、当前调度租约、停滞秒数、恢复建议和创建/更新审计字段。

### 3.3 Workspace 影响分页

支持 `status`、`riskLevel`、Workspace 关键字和分页过滤，固定按 `itemOrder, workspaceId` 排序。

每项提供：

- Workspace 编码、名称、环境、风险等级；
- 当前策略和版本身份；
- 执行状态、尝试次数、租约、失败摘要和执行时间；
- Workspace 快照身份、校验和、过期时间；
- 结构化参数变化、影响汇总和变化报告数量。

没有成功快照时 `impactComparison=null`，不伪造零值影响结果。

### 3.4 时间线

时间线投影 `tpip_audit_event` 中属于任务的安全字段，按 `occurredAt, id` 正序返回。`limit` 为 1～500；服务端额外读取一条，仅在真实存在下一条时返回 `truncated=true`。

## 4. 操作能力模型

`allowedActions` 不是前端权限模型，而是当前资源状态下的服务端能力描述。每个动作均返回：

```json
{"action":"SEAL","enabled":false,"disabledReasonCode":"JOB_NOT_READY"}
```

首期动作集合固定为：

- `REPRIORITIZE`；
- `CANCEL`；
- `RETRY_FAILED`；
- `SEAL`；
- `VIEW_SEALED_SNAPSHOT`。

过期事实优先产生 `JOB_EXPIRED`。这能覆盖维护调度尚未把数据库状态转换为 EXPIRED 的时间窗口。命令端同样增加了 reprioritize 的 `expires_at` 条件，避免只依赖 UI 门禁。

UI 只能使用 `allowedActions.enabled` 控制交互展示；真正执行时命令 API 仍重新校验状态、过期时间和 rowVersion。

## 5. 认证边界

首期 UI 无登录页和前端路由权限。查询接口不要求 `X-Operator`；管理写操作仍由已有命令接口接收 `X-Operator` 审计标签。前端不得保存数据库口令、Automation Token 或第三方 Secret，开发服务器只监听 loopback 地址。

API Client 应预留可插拔认证请求增强接口，但当前实现为空，不模拟 JWT。未来接入 OIDC/RBAC 时保持本查询 DTO 和业务页面稳定。

## 6. 数据库影响

v1.4 不新增数据库迁移。现有 V38-V40 表、外键和索引可以覆盖当前本地规模的列表、详情、Workspace 影响和审计时间线查询。共享或大规模部署前，应基于真实查询基数与执行计划评估专用搜索索引或读库，而不是提前冗余持久化 UI DTO。

## 7. 验收标准

1. 列表组合过滤、固定排序和分页元数据正确；
2. 详情展示调度态、停滞时间和恢复建议；
3. Workspace 影响支持状态、风险、关键字和分页；
4. 原始快照证据被解析为稳定结构化对比；
5. 时间线只投影安全审计字段并准确表达截断；
6. 所有命令操作均提供 enabled 和稳定禁用原因码；
7. 过期任务在状态维护存在延迟时也不能执行修改动作；
8. 原有命令、Worker 和领域模型契约不被 UI 查询破坏；
9. 无登录模式只适用于本地单用户 loopback 环境；
10. 查询验收不产生任何业务写入。

## 8. 下一阶段门禁

v1.4 完成后，下一阶段正式进入 UI 工程与页面建设。开始该阶段前必须明确提醒用户“现在开始 UI”；首期仍不建设登录和 RBAC。

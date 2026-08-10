# TPIP UI 提醒批次不可变命令审计时间线 v0.18

## 1. 阶段目标

v0.18 补齐 v0.17 受控写链路的运营证据闭环。提醒批次详情不再只能看到当前状态和业务时间戳，而是可以读取每次命令实际写入的不可变审计事件。

本阶段继续采用本地单用户模式，不实现登录、OIDC/JWT 或 RBAC。`X-Operator` 默认使用 `local-ui`，未来接入认证环境时只替换身份来源，不改变审计查询契约。

## 2. 设计原则

- 审计事实来源只允许是 `tpip_audit_event`；
- 批次表只用于校验资源存在和提供当前 Row Version 上下文；
- 不根据当前状态、`approvedAt`、`cancelledAt` 或 `dispatchedAt` 反向伪造历史；
- 没有审计事件时返回空集合，由 UI 明确显示“没有可读取的不可变审计事件”；
- 旧事件的 `event_detail` 为空时仍可返回事件 ID、类型、操作者、摘要和时间；
- 新事件使用结构化 JSON 保存版本和关联关系，不新增数据库表或迁移。

## 3. 审计事件模型

批次命令统一使用资产类型 `DRIFT_GOVERNANCE_REMINDER_BATCH`，资产编码为稳定的 `batchCode`。

| 事件 | 含义 | 结构化证据 |
|---|---|---|
| `..._CREATED` | 创建 DRAFT | 状态、RV、成员数量、被替代批次 |
| `..._APPROVED` | 批准但未提交 | 状态、RV、替代来源 |
| `..._CANCELLED` | 取消并释放占用 | 状态、RV、治理原因 |
| `..._REPLACEMENT_LINKED` | 原批次建立被替代血缘 | 状态、RV、原因、新批次 ID |
| `..._DISPATCHED` | 提交到 Outbox | 状态、RV、Outbox ID、替代来源 |

每条事件都保留独立 UUID、操作者、摘要和 `occurredAt`。写入与对应状态转换处于同一服务事务；审计写入失败会使命令整体回滚。

## 4. 查询契约

```text
GET /control/v1/verification-drift-workbench/
    governance-reminder-batches/{batchId}/timeline
```

响应包含：

- `batchId`、`batchCode`；
- 查询时的 `currentRowVersion`；
- 最多 100 条按 `occurredAt,id` 升序排列的事件；
- 事件的操作者、摘要、状态、Row Version、原因、替代关系、Outbox 和 UUID。

Repository 端同时校验批次编码和查询上限。接口只读，不接受客户端提供资产编码，避免跨批次读取。

## 5. UI 呈现

提醒批次详情新增“不可变命令审计”区域：

- 标题同时展示当前 Row Version 和事件数量；
- 不同命令使用稳定的中文标签和时间线颜色；
- 每条事件展示操作者、时间、状态和当时的 Row Version；
- 取消与替代展示治理依据；
- 替代展示 `replaces/replacedBy`；
- 提交展示 Outbox ID，并可与下方投递投影交叉核对；
- 未识别的未来事件保留原始事件类型，不静默丢弃。

命令成功后，批次详情、投递状态和审计时间线并行刷新，页面不会继续显示旧版本证据。

## 6. 真实浏览器验收

2026-08-09 使用隔离 Workspace `28`、Control Plane `18082` 和 UI `18100` 完成：

1. 预置 DRAFT 批准后出现 `APPROVED / RV 2 / local-ui` 审计；
2. 再取消后新增 `CANCELLED / RV 3`，治理原因完整展示，成员重新进入到期候选；
3. 从候选创建新 DRAFT 后出现 `CREATED / RV 0`；
4. 替代该批次后，原批次按顺序显示创建、取消和血缘建立三条事件，新批次显示 `替代 #15`；
5. 替代批次批准并提交后，时间线显示 `DRAFT → APPROVED → DISPATCHED`，提交事件关联 `Outbox #18`；
6. 下方投递投影同时显示 `PENDING · UNROUTED`，没有把 Outbox 创建误报为送达；
7. 浏览器无业务 JavaScript error。

验收完成后服务优雅停止，夹具执行 `cleanup` 和 `verify-clean`，Workspace、动态批次、Outbox 与审计事件均无残留；18082、18100 无监听进程。

## 7. 回归结果

- Control Plane 相关 Maven 模块：177 项测试通过；
- Java 21 Maven 14 模块全量：308 项测试通过，失败和错误均为 0；
- UI：23 个测试文件、42 项测试通过；
- TypeScript 检查与 Vite 生产构建通过；
- 提醒批次页面分块约 24.77 kB（gzip 7.90 kB）；
- 最大公共分块仍为 Element Plus 433.44 kB（gzip 147.52 kB）。

## 8. 下一阶段建议

v0.19 建议将本次浏览器命令场景固化为自动化回归门禁，并增加“审计完整性检查”：对终态批次验证必要事件、Row Version 连续性、替代双向血缘和 Outbox 关联一致性。自动门禁完成后，再考虑跨 Workspace 审计检索与导出。

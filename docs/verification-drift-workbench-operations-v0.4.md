# Verification 漂移治理工作台运营闭环 v0.4

## 1. 目标与边界

v0.4 在既有单报告确认/接受/忽略命令之上增加负责人分派、批量确认、批量处置、Dry Run、幂等回放和完整
命令证据。它提升本地治理效率，但不改变不可变 DriftReport、Baseline 和已发布资产。

当前仍是本地单用户模式：`X-Operator` 只作为审计标签，不是可信身份。v0.4 不模拟 RBAC，也不宣称已经实现
职责分离；未来接入统一身份后，命令已显式携带 Workspace 作用域，可直接增加权限门禁。

## 2. API

```text
POST /control/v1/verification-drift-workbench/governance-reviews:assign
POST /control/v1/verification-drift-workbench/governance-reviews:acknowledge
POST /control/v1/verification-drift-workbench/governance-reviews:dispose
GET  /control/v1/verification-drift-workbench/governance-operations/{commandKey}
```

三个命令接口都要求：

```text
Idempotency-Key: 1..100 characters
X-Operator: 1..100 characters
```

每个请求必须提供一个 `workspaceId` 和 1 至 100 个 `{reportId,rowVersion}`。同一批次不能重复选择报告，也不能
跨 Workspace。`reportId + rowVersion` 是操作选择快照，正式执行时必须重新校验。

## 3. 分派

分派只允许作用于 `OPEN/ACKNOWLEDGED` 报告。分派和转派都会更新：

- `assigneeCode`
- `assignedBy`
- `assignedAt`
- `assignmentNote`
- `rowVersion`

已解决报告保留最后一次分派证据，不能重新分派。工作台列表响应包含分派证据，支持 `assigneeCode` 精确筛选；
汇总增加已分派和未分派可行动报告数量。

## 4. 批量确认与处置

- 批量确认只接受 `OPEN -> ACKNOWLEDGED`；
- 批量忽略只接受 `ACKNOWLEDGED -> DISMISSED`；
- 批量接受只接受 `ACKNOWLEDGED -> ACCEPTED`，并对每项重新校验 PASSED REGRESSION、服务端执行证据、
  Workspace 和 FixtureSuiteVersion 兼容性；
- 接受成功仍为每份报告创建不可变后继基线，不原地修改旧基线。

正式批量命令采用全有或全无：任一目标不存在、跨 Workspace、状态错误、rowVersion 过期或回归不兼容，整批
状态为 `REJECTED`，`appliedCount=0`。该语义避免批量接受出现孤立后继基线或难以解释的部分处置。

## 5. Dry Run 与幂等

`dryRun=true` 执行与正式命令相同的资格检查，但不分派、不改变 Review、不创建后继基线。结果状态固定为
`PREVIEWED`，逐项返回：

- 当前状态和 rowVersion；
- 当前负责人；
- 是否合格；
- 稳定原因码与安全错误说明；
- 目标状态。

正式命令存在不合格项时返回 `REJECTED`；全部合格并提交成功时返回 `APPLIED`。

`Idempotency-Key` 与规范请求 checksum 绑定。同一键、同一请求返回已保存结果并标记
`idempotentReplay=true`；同一键提交不同请求返回校验错误，不会重用旧结果或产生第二次操作。

## 6. 完整审计

V35 新增 `tpip_verification_drift_bulk_operation`，保存：

- 命令键、Workspace、类型、Dry Run 标记和操作者；
- 请求 SHA-256；
- `PREVIEWED/REJECTED/APPLIED` 状态和数量；
- JSON 请求快照与逐项结果快照；
- 创建时间。

每个批次同时写入 `tpip_audit_event`。正式分派还写逐报告 `VERIFICATION_DRIFT_ASSIGNED`；确认、接受和忽略
继续使用既有逐报告审计事件。审计回查 API 返回操作者、checksum、请求和结果，但不返回 DriftReport 原始证据、
Fixture、Provider 报文或 Secret。

## 7. 事务与失败语义

- 资格拒绝是预期业务结果，会被持久化，不把事务标记为回滚；
- 正式应用在一个事务中执行，任何并发写失败都会整体回滚；
- 批量操作证据与正式状态变更同事务提交；
- 数据库检查约束保证数量与操作状态一致；
- 幂等键主键和 Review 乐观锁负责多实例最终仲裁。

## 8. 验收标准

1. 分派字段要么全部为空，要么完整存在；
2. 工作台可按负责人筛选并统计分派覆盖率；
3. Dry Run 不改变 Review 或 Baseline；
4. 正式命令任一项不合格时整批零修改；
5. 批量接受只为兼容的 PASSED REGRESSION 创建后继基线；
6. 幂等重放稳定，同键异请求被拒绝；
7. 审计回查可重建操作者、请求、结果和逐项原因；
8. API 不暴露原始漂移证据和敏感配置。


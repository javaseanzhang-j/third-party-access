# TPIP UI 浏览器验收与可回滚夹具 v0.16

## 1. 阶段目标

v0.16 为治理评估、运营度量和提醒批次页面建立真实浏览器验收门禁。验收数据不借用现有 Workspace，也不通过
模糊条件清理；每次运行创建隔离资产，使用 manifest 记录全部主键，并在服务停止后逆向回滚和二次核验。

本阶段仍然只验证 UI 查询与详情交互，不在 UI 接入物化、批准、提交、取消或替代命令。

## 2. 可重复验收工具

工具位于 [`e2e/ui-governance-workbench`](../e2e/ui-governance-workbench/README.md)：

- `UiGovernanceFixture.java` 使用 JDK 21 和 MySQL JDBC 创建、清理并核验隔离资产；
- `fixture.sh` 负责编译和运行，不保存数据库密码；
- `manifest.properties` 在运行时生成，记录 Workspace、策略版本、验证运行、基线、漂移报告、执行账本、批次、
  Outbox 和渠道投递的精确 ID；
- `cleanup` 在一个事务内按外键逆序删除，并对 manifest 中的每个顶层资产执行零残留断言；
- `verify-clean` 提供清理后的独立只读复核。

夹具自带一个 Workspace 专属 ACTIVE 策略，避免已有 Global 策略或未来默认值变化使验收结论漂移。策略固定为
1 小时超期、1 天聚合、1 天提醒间隔、最多 3 次提醒，且不抑制任何漂移。

## 3. 夹具状态模型

```text
Workspace
  ├─ ACTIVE Workspace Policy + PUBLISHED Version
  ├─ PASSED baseline run → immutable baseline
  ├─ PASSED regression run → DRIFTED report → OPEN review
  │    └─ UI_V016_FIXTURE / EVIDENCE_CHANGED
  ├─ READY execution ledger (reminder 1 / 3)
  └─ reminder batches
       ├─ CANCELLED original ──replaced by──▶ DISPATCHED replacement
       │                                      └─ ROUTED/DELIVERED Outbox
       │                                           └─ DELIVERED simulated channel
       └─ DRAFT batch reserving reminder 2
```

模拟渠道只用于本地查询投影验收，不包含 Endpoint、Secret、消息正文或真实外部发送。

## 4. 真实浏览器验收结果

2026-08-09 使用 MySQL 8.4、Control Plane `18082`、UI `18100` 和隔离 Workspace `25` 完成验收：

### 4.1 治理评估

- 策略解析为 `WORKSPACE_POLICY`，负责人为 `ui-v016-owner`；
- 可行动报告 1、提醒候选 1、完全抑制 0、冻结执行账本 1；
- 报告状态为 `OPEN / CANDIDATE`，展开后显示
  `UI_V016_FIXTURE / EVIDENCE_CHANGED / ACTIVE`；
- 执行账本显示 `READY`、提醒预算 `1 / 3`、聚合键和评估 checksum。

### 4.2 治理运营度量

- 有效 SLA 为 Workspace 策略的 1 小时；
- 当前待处置 1、已突破 SLA 1、未分派且超期 1；
- 日趋势在报告创建日显示创建 1；
- 漂移聚合簇精确显示 `UI_V016_FIXTURE + EVIDENCE_CHANGED`，报告、待处置和超期均为 1。

### 4.3 提醒批次资产

- 批次总数 3：DRAFT 1、DISPATCHED 1、CANCELLED 1；
- 活动占用 1，终态投递成功率 100%；
- Outbox 路由为 ROUTED 1，渠道投递为 DELIVERED 1；
- 替代血缘显示原批次被替代、替代批次指向原批次；
- 详情抽屉显示成员快照、`DELIVERED · ROUTED`、模拟渠道 DELIVERED 和替代差异 `UNCHANGED 1`。

稳定后的新浏览器标签重新顺序访问三条路由，页面控制台 `error/warn` 为 0。首次冷启动期间 Vite 因首次优化
Element Plus 依赖触发过一次动态模块加载告警，自动重载后恢复；这是开发服务器冷启动行为，不影响生产构建，
后续可通过验收前 warm-up 或显式依赖预构建消除噪声。

## 5. 回滚与回归结果

```text
fixture.seed=complete
fixture.cleanup=complete
fixture.verifyClean=complete

UI Test Files  21 passed (21)
UI Tests       38 passed (38)
vue-tsc / tsc / vite build passed

Maven reactor 14 modules BUILD SUCCESS
Java Tests 306, Failures 0, Errors 0, Skipped 0
```

Control Plane 和 Vite 均已停止，`18082`、`18100` 无监听进程。数据库密码和临时 manifest 未写入版本库，
隔离 Workspace 及全部关联资产已清理并二次核验无残留。

## 6. 下一阶段建议

UI v0.17 可以在 v0.16 门禁上接入受控写操作，建议按风险从低到高分批落地：

1. 显式物化单个候选为执行账本，并展示幂等结果；
2. 从到期账本创建 DRAFT 批次，不消耗提醒预算；
3. 批准、取消和替代批次，强制原因、`rowVersion` 与操作后二次读取；
4. 最后接入提交 Outbox，使用独立确认页展示成员、渠道路由范围和预算变化，禁止自动重放冲突请求。

本地单用户阶段继续不实现登录，但所有命令仍保留审计 Operator、乐观锁、幂等键和明确确认边界，便于未来接入
OIDC/RBAC 时直接替换身份来源。

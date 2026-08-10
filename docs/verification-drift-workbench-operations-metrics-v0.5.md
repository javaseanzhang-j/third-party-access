# Verification 漂移治理工作台运营度量与 SLA v0.5

## 1. 目标与边界

v0.5 在 v0.4 可执行运营闭环之上增加 Workspace 级只读度量：当前待办 SLA、负责人负载、窗口内处置效率、
批量命令质量和每日趋势。指标直接读取 DriftReport、Review 和批量命令事实账本，不创建第二套事实，不修改
Review/Baseline，也不启动扫描、提醒或自动处置。

本阶段提供运营快照，不宣称是长期监控系统。需要告警、长期留存或跨 Workspace 企业级 SLO 时，应由后续
Metrics/Telemetry 适配器采集，而不是把当前查询结果反写数据库。

## 2. API

```text
GET /control/v1/verification-drift-workbench/governance-metrics
    ?workspaceId={workspaceId}
    &windowDays={1..90，默认30}
    [&slaHours={1..8760，v0.6 起仅作为显式分析覆盖}]
```

`workspaceId` 必填，避免无意跨 Workspace 聚合负责人标识和运营数据。当前本地单用户模式仍不启用 RBAC；
未来接入可信身份后，该接口应授予 Workspace 治理查看权限。

v0.6 起，不传 `slaHours` 时默认解析已发布并激活的治理策略；策略来源、版本和覆盖语义见
`docs/verification-drift-workbench-policy-bound-sla-v0.6.md`。

## 3. 时间与 SLA 口径

- `snapshotAt` 为本次请求的统一计算时刻；一次响应中的所有指标共享该时刻；
- 日趋势使用 UTC 日历日，包含当前日，应用层为无事件日期补零；
- 时间窗口从首日 UTC 00:00 到 `snapshotAt`，最多 90 个日历日；
- 当前待办 SLA 从 DriftReport `created_at` 起算，只统计 `OPEN/ACKNOWLEDGED`；
- `age >= slaHours` 计为 breached，已解决报告不再进入当前待办 SLA；
- 历史处置 SLA 使用 `resolved_at - report.created_at`，不使用可能被分派更新的 `updated_at`。

## 4. 指标结构

### 4.1 Backlog

- 当前可行动、SLA 内、已超期数量；
- 已分派、未分派、未分派且超期数量；
- 超期率：`breached / actionable`；
- 分派覆盖率：`assigned / actionable`；
- 最老待办时间、最大龄期和平均龄期。

分母为零时，百分比稳定返回 `0.00`，不返回 `NaN/null`。

### 4.2 Resolution

- 窗口内解决、SLA 内解决、超期解决数量；
- 接受与忽略数量；
- SLA 达标率：`withinSla / resolved`；
- 平均和最大端到端处置小时数。

### 4.3 Commands

- `PREVIEWED/APPLIED/REJECTED` 命令数量；
- 正式执行成功率：`APPLIED / (APPLIED + REJECTED)`，Dry Run 不进入分母；
- 请求、资格通过、实际应用和拒绝的成员数量。

### 4.4 Assignees 与 Daily

负责人负载只统计当前可行动报告，返回待办数、超期数和最老待办龄期。未分派作为
`assigneeCode=null, assigned=false` 的独立负载行，不伪造人员编码。

每日趋势返回漂移报告创建、Review 解决、Dry Run、正式应用和正式拒绝数量。趋势是事实事件计数，不根据当前
状态倒推历史库存；库存曲线需要事件流或每日快照后再建设。

## 5. 数据与安全边界

- v0.5 复用 V28、V30 和 V35 既有表及索引，不新增数据库迁移；
- Repository SQL 只投影计数、时间和负责人编码，不读取 Report Document、Fixture、Provider 报文或 Secret；
- API 不返回确认说明、处置原因、分派说明和批量命令 JSON 证据；
- 所有查询运行在 Spring 只读事务中；API 调用不写 AuditEvent，避免只读刷新制造审计噪声；
- 完整操作审计仍通过 v0.4 `governance-operations/{commandKey}` 回查。

## 6. 验收标准

1. Workspace 23 的指标可在 MySQL 8.4 上执行并返回 HTTP 200；
2. 当前待办、处置和命令汇总可与 V28/V35 事实行对账；
3. Dry Run 不进入正式命令成功率分母，但在汇总和日趋势中可见；
4. 没有待办或正式命令样本时，所有比率返回 `0.00`；
5. 时间窗口稳定包含指定数量的 UTC 日期且缺失日补零；
6. `windowDays > 90`、非法 Workspace ID 或 SLA 范围被拒绝；
7. 请求前后 Review、Baseline、批量命令和 AuditEvent 数量不变；
8. 响应不包含原始证据、人员输入说明和敏感配置。

## 7. 后续演进

1. 基于该 API 建设待办、负责人负载、趋势与聚合簇的交互界面；
2. SLA 阈值绑定已由 v0.6 落地；后续增加策略切换前后的影响对比；
3. 增加 Prometheus 指标、长期 SLO、告警和容量基线；
4. 接入 OIDC/RBAC 后增加 Workspace 数据权限和负责人目录校验。

## 8. UI 阶段提醒门禁

进入任何 Web UI、管理后台、可视化看板或交互式工作台实施阶段前，Codex 必须先明确提醒用户“下一阶段将开始
UI 建设”，说明拟实现的页面范围和依赖，然后再开始 UI 相关设计或代码实施。该提醒门禁不阻塞 UI 之前的
后端标准、策略绑定、指标和接口建设。

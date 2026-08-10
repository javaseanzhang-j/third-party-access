# Workspace 服务端 Verification Engine 与 FixtureSuite 资产化 v0.2

## 1. 目标

本阶段把 Workspace 的“验证结果登记”改造成“服务端真实执行”，并把零散测试数据提升为可版本化、
可发布、可复用的 FixtureSuite 资产。核心约束是：客户端只能选择验证输入，不能声明验证结论。

## 2. 资产模型

```text
IntegrationBinding 1 --- n FixtureSuite
FixtureSuite       1 --- n FixtureSuiteVersion
FixtureSuiteVersion 1 -- n FixtureCase

ConfigurationWorkspace 1 --- n VerificationRun
VerificationRun         1 --- n VerificationCheck
```

- `FixtureSuite`：绑定到一个 Binding 的稳定资产身份，使用全局唯一 `suiteCode`。
- `FixtureSuiteVersion`：带内容 SHA-256 的不可变快照；只允许 `DRAFT -> PUBLISHED`。
- `FixtureCase`：当前支持 `OUTBOUND_REQUEST` 与 `INBOUND_RESPONSE`，保存源报文和 Fixture Assertion Profile 1.0
  断言文档；旧期望报文和期望诊断码继续兼容。
- `VerificationRun`：服务端任务，状态为 `RUNNING / PASSED / FAILED`。
- `VerificationCheck`：一个真实检查项的结果、结构化详情、证据与执行时间。

原 `tpip_test_case` 仅保留历史读取和审计价值，不再参与发布门禁。

## 3. 配置与执行流程

```text
创建 FixtureSuite
  -> 创建不可变 FixtureSuiteVersion（计算内容 checksum）
  -> 发布 FixtureSuiteVersion
  -> Workspace 引用已发布 BindingVersion
  -> POST /workspaces/{id}:verify
  -> 持久化 RUNNING VerificationRun
  -> 服务端依次执行检查并记录 VerificationCheck
  -> 汇总 PASSED/FAILED
  -> 全部通过时 Workspace: DRAFT -> VERIFIED
  -> 提交评审时再次校验 PASSED Run 具有真实 Check
```

一次 FULL 验证当前包含：

1. `BINDING_DEPENDENCY_CLOSURE`：BindingVersion 存在且已发布。
2. `FIXTURE_SUITE_ELIGIBLE`：Suite 属于同一 Binding，Suite 有效且版本已发布。
3. `BUNDLE_PREVIEW`：完整依赖可编译为确定性 Bundle Manifest。
4. `ENDPOINT_PROBE`：对冻结 Endpoint 执行真实连通性探测。
5. `FIXTURE.{caseCode}`：使用已发布 MappingVersion 执行 JSONPath 映射，再执行 SUCCESS、诊断码、JSONPath、
   JSON Schema 和受限 Policy 表达式断言；每条断言结果写入 Check 证据。
6. 显式 REMOTE_CALL Fixture：在安全门禁允许时，通过候选 Bundle 的完整 Runtime Pipeline 调用测试端点，
   执行 HTTP Status、Header 和 Provider Body 断言，并保存脱敏证据。

检查结果采用失败汇总策略：单个检查失败会留下证据并继续执行其余可执行检查；基础依赖或 FixtureSuite
资格不成立时提前终止后续检查。只要存在失败项，任务就是 FAILED，Workspace 保持 DRAFT。

## 4. API 契约

FixtureSuite 配置：

```text
POST /control/v1/fixture-suites
GET  /control/v1/fixture-suites?bindingId={bindingId}
GET  /control/v1/fixture-suites/{suiteId}
POST /control/v1/fixture-suites/{suiteId}/versions
GET  /control/v1/fixture-suites/{suiteId}/versions
GET  /control/v1/fixture-suites/{suiteId}/versions/{versionId}
POST /control/v1/fixture-suites/{suiteId}/versions/{versionId}:publish
```

服务端验证：

```text
POST /control/v1/workspaces/{workspaceId}:verify
GET  /control/v1/workspaces/{workspaceId}/verification-jobs
GET  /control/v1/verification-jobs/{runId}
GET  /control/v1/verification-jobs/{runId}/checks
POST /control/v1/verification-jobs/{runId}:retry
```

验证命令只包含：

```json
{
  "fixtureSuiteVersionId": 42,
  "rowVersion": 0
}
```

旧写接口 `POST /control/v1/workspaces/{id}/verifications` 已移除。兼容读取接口
`GET /control/v1/workspaces/{id}/verifications` 暂时保留。

## 5. 数据库落地

迁移脚本：`database/migration/V22__workspace_verification_engine_and_fixture_suite.sql`、
`V23__workspace_verification_single_active_job.sql`、`V24__fixture_case_assertion_document.sql` 和
`V25__fixture_remote_call_execution_mode.sql`、`V26__verification_baseline_and_drift_report.sql`、
`V27__regression_policy_scheduler.sql`、`V28__verification_drift_review_and_baseline_lineage.sql`、
`V29__regression_policy_version_baseline.sql`、`V30__verification_drift_workbench_projection.sql`、
`V31__drift_governance_policy_assets.sql`、`V32__drift_governance_execution_ledger.sql`、
`V33__drift_governance_reminder_batch.sql`、
`V34__drift_governance_reminder_batch_operations.sql`、
`V35__verification_drift_workbench_operations.sql`。

新增表：

- `tpip_fixture_suite`
- `tpip_fixture_suite_version`
- `tpip_fixture_case`
- `tpip_verification_check`
- `tpip_verification_baseline`
- `tpip_verification_drift_report`
- `tpip_drift_governance_policy`
- `tpip_drift_governance_policy_version`
- `tpip_drift_governance_execution`
- `tpip_drift_governance_reminder_batch`
- `tpip_drift_governance_reminder_batch_member`
- `tpip_verification_drift_bulk_operation`

现有 `tpip_verification_run` 继续作为 Job 主表，开始时保存 RUNNING，结束时原子更新为 PASSED 或 FAILED。
FixtureSuiteVersion 通过 `(suite_id, version_no)` 与 `(suite_id, content_checksum)` 双唯一约束避免版本冲突和
相同内容重复创建；Case 在一个版本内按 `case_code` 唯一。
V23 使用生成列和唯一索引在数据库边界保证一个 Workspace 同时最多存在一个 RUNNING Job，避免多实例或
并发请求绕过应用层检查。
V24 为 FixtureCase 增加不可变的 JSON 断言文档；断言内容进入 FixtureSuiteVersion checksum，历史 Case
继续使用旧期望字段，无需原地迁移。

治理提醒可观测性 v0.3 直接复用 V32 至 V34 的执行、批次、Outbox 和 Delivery 账本及现有索引，提供批次差异、
投递关联和 Workspace 指标，不新增迁移或物化投影，见
`docs/verification-drift-governance-reminder-observability-v0.3.md`。

V35 为 DriftReview 增加完整负责人分派证据，并新增 Workspace 作用域的幂等批量命令账本，支持 Dry Run、
全有或全无确认/接受/忽略和审计回查，见 `docs/verification-drift-workbench-operations-v0.4.md`。

V26 在 VerificationRun 之上增加不可变 `VerificationBaseline` 与 `VerificationDriftReport`，支持使用同一
FixtureSuiteVersion 手动执行 `REGRESSION` 并检测检查状态、结果指纹和 Evidence 指纹变化。详细标准见
`docs/fixture-regression-baseline-drift-v0.1.md`。

V27 增加默认关闭的 RegressionPolicy 自动调度、数据库租约、失败退避和漂移 Outbox 事件，详细标准见
`docs/regression-policy-scheduler-v0.1.md`。

V28 增加漂移治理状态机、乐观锁决策和不可变后继基线血缘，详细标准见
`docs/verification-drift-governance-v0.1.md`。

V29 将实际执行基线固化到不可变 RegressionPolicyVersion，只允许显式切换到已接受的直接后继，详细标准见
`docs/regression-policy-baseline-transition-v0.1.md`。

基于 V26、V28 和 V29 现有索引，控制面提供基线血缘树、漂移趋势和 RegressionPolicy 影响范围三个只读
查询，无需新增数据库迁移，详细标准见
`docs/verification-baseline-lineage-impact-analysis-v0.1.md`。

V30 增加可从不可变 DriftReport JSON 重建的规范化漂移项投影，为跨 Workspace 工作台提供分页、超期和同类
漂移聚合，详细标准见 `docs/verification-drift-workbench-v0.1.md`。

V31 增加 Workspace/全局两级漂移治理策略和不可变版本，配置超期、聚合、责任人、提醒预算及抑制规则，
并提供 Workspace 优先、全局次之、内置默认兜底的确定性解析。详细标准见
`docs/drift-governance-policy-assets-v0.1.md`。

V32 增加人工显式物化的治理执行账本，冻结策略版本、只读评估、聚合键和提醒预算，不创建通知。详细标准见
`docs/verification-drift-governance-execution-ledger-v0.1.md`。

V33 增加 DRAFT 提醒预览批次、人工批准和显式提交；只有 Notification Outbox 与执行账本提醒进度在同一事务
中成功提交后才消耗预算，且默认不启用自动扫描。详细标准见
`docs/verification-drift-governance-reminder-batch-v0.1.md`。

V34 增加批次取消、替代血缘、活动提醒占用释放，以及默认关闭且只能创建 DRAFT 的预览生成器。详细标准见
`docs/verification-drift-governance-reminder-operations-v0.2.md`。

V35 增加负责人分派和幂等批量治理命令账本；其上的 v0.5 只读运营度量复用既有事实表，提供待办 SLA、负责人
负载、处置效率和命令趋势，不新增数据库迁移。详细标准见
`docs/verification-drift-workbench-operations-v0.4.md` 和
`docs/verification-drift-workbench-operations-metrics-v0.5.md`。

策略绑定 SLA v0.6 将指标默认阈值绑定到 V31 的当前解析策略版本，返回来源、版本和秒级口径证据；显式覆盖
仅用于自描述的只读分析。详细标准见 `docs/verification-drift-workbench-policy-bound-sla-v0.6.md`。

策略切换影响分析 v0.7 基于 V30 投影比较当前策略与候选不可变版本的超期、抑制和提醒候选集合变化，为发布
和激活提供只读评审依据。详细标准见 `docs/verification-drift-policy-impact-analysis-v0.7.md`。
V36 将分析结果固化为可过期的不可变影响快照；Workspace 策略发布和激活分别校验并一次性消费快照，防止候选
或当前基线漂移后继续使用旧评审结论。详细标准见 `docs/verification-drift-policy-impact-snapshot-gate-v0.8.md`。
V37 增加 Global 聚合头与 Workspace 子快照关联，按未被专属策略遮蔽的真实 Workspace 计算覆盖 checksum；
Global 发布和激活必须通过覆盖完整性及逐 Workspace 基线校验。详细标准见
`docs/verification-global-drift-policy-impact-snapshot-gate-v0.9.md`。
V38 将 Global 影响计算资产化为持久任务和 Workspace 分片账本，提供并行租约领取、失败续算、统一观察时间和
显式封板；封板产物继续使用 V37 聚合快照。详细标准见
`docs/verification-global-drift-policy-impact-job-v1.0.md`。

## 6. 运行与演进边界

当前采用同步执行，但 Verification Engine 已通过 `WorkspaceVerificationExecutor` 端口与编排服务解耦，
而且远程探测不包裹在长数据库事务中。RUNNING Job 会先落库，因此进程中断不会丢失任务事实。

控制面通过 `tpip.workspace-verification.timeout`（默认 10 分钟）和定时恢复器处理长时间停留在
RUNNING 的任务：写入 `ENGINE_TIMEOUT` Check 后将任务收敛为 FAILED，Workspace 保持 DRAFT。
恢复开关、轮询周期和单批数量均可通过环境变量配置。失败任务保留最初选择的
`fixtureSuiteVersionId`，可调用 `POST /verification-jobs/{runId}:retry` 创建新 Run；PASSED 或
RUNNING 任务不能重试。

```text
TPIP_WORKSPACE_VERIFICATION_RECOVERY_ENABLED=true
TPIP_WORKSPACE_VERIFICATION_TIMEOUT=10m
TPIP_WORKSPACE_VERIFICATION_RECOVERY_POLL_INTERVAL=1m
TPIP_WORKSPACE_VERIFICATION_RECOVERY_BATCH_SIZE=100
```

后续达到以下任一条件时迁移到 Worker：单套件超过 100 个 Case、单次验证超过 30 秒、需要并发配额、
取消/重试、定时回归或分布式执行。迁移时保持 API 与领域模型不变，由控制面创建 Job 并投递事件，Worker
实现同一执行端口，同时增加租约、心跳、超时和 RUNNING 孤儿任务恢复。

## 7. 验收标准

- 客户端不能提交 `status/totalCount/passedCount/failedCount/evidenceUri` 来改变 Workspace 状态。
- 未发布或跨 Binding 的 FixtureSuiteVersion 必须失败。
- 每个真实检查均能查询到结构化结果和证据。
- 任一检查失败时 Workspace 不进入 VERIFIED。
- 没有 VerificationCheck 的历史 PASSED 记录不能通过提交评审门禁。
- 全量 `mvn test` 通过；Customer Lookup E2E 使用 FixtureSuite 和新验证端点。

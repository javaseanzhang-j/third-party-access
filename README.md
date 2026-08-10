# Third-Party Integration Platform（TPIP）

企业第三方集成平台参考实现，遵循EESIS标准和本仓库`docs/`中的架构基线。

工程目录、模块职责和关键文件导航见
[`docs/tpip-project-directory-guide.md`](docs/tpip-project-directory-guide.md)。

## 使用文档

- [`docs/tpip-platform-user-manual.md`](docs/tpip-platform-user-manual.md)：平台功能、启动、UI、运维和安全边界；
- [`docs/tpip-business-system-integration-guide.md`](docs/tpip-business-system-integration-guide.md)：业务系统调用契约和第三方接口完整接入流程；
- [`docs/tpip-product-model-and-ui-redesign-v0.23.md`](docs/tpip-product-model-and-ui-redesign-v0.23.md)：第三方系统、接入通道、第三方接口、serviceCode、多目标适配与路由的统一产品模型；
- [`e2e/customer-lookup/README.md`](e2e/customer-lookup/README.md)：可直接执行的 Customer Lookup 接入样例。

## 当前状态

当前完成工程骨架：

- 13个Maven模块；
- Java 21与Spring Boot 3.5.7构建基线；
- 标准调用契约；
- Catalog最小领域模型；
- Mapping稳定身份、不可变版本、JSONPath Profile 1.0编译器与IR；
- Policy Type Registry、Policy稳定身份/不可变版本、Policy DSL v0.1编译器与IR；
- BindingVersion依赖闭包冻结、发布门禁和Bundle Preview编译；
- ConfigurationWorkspace验证/审批状态机、不可变Bundle持久化与制品下载；
- Runtime Bundle Resolver、三层checksum校验、L1缓存和有界Last Known Good回退；
- Deployment预热、激活、确定性灰度、单调扩量、回滚和动态路由；
- Runtime标准契约校验、双向Mapping、受控Policy Hook与HTTP Transport执行流水线；
- Secret Resolver SPI、受限环境变量适配器、API Key Policy与Runtime预热兼容性门禁；
- Runtime分阶段指标、安全审计、Readiness和Deployment健康自动回滚；
- Worker自动发现Canary、Prometheus窗口取证、Redis防重与冷却、工作负载认证；
- 连续健康窗口、严重故障立即保护、告警确认/恢复与事务通知Outbox；
- 通知任务租约、指数退避、Dead Letter人工重放与Webhook Provider；
- 事件与渠道投递分层、独立渠道重试、稳定渠道路由与Dispatcher Prometheus指标；
- 通知渠道/路由不可变版本资产、发布门禁、Delivery执行快照与Secret Reference解析；
- 通知环境隔离、渠道/路由乐观锁启停、NO_MATCH重路由与未匹配事件指标；
- 通知投递尝试确定性NDJSON归档、双重SHA-256回读验证、法律保全与默认关闭的安全清理门禁；
- 归档标准窗口自动编排、跨实例租约、写一次制品、验证历史、周期性回读抽检与Prometheus指标；
- 可切换本地/S3归档存储、对象版本绑定、Versioning/Object Lock门禁与存储健康检查；
- Runtime执行端口；
- Control Plane、Runtime和Worker启动入口；
- 最小单元测试。

当前明确未接入：

- Vault、云厂商 Secret Manager 等生产级 Secret Resolver 适配器；
- 外部对象存储与Bundle签名服务；
- OAuth2、签名、加解密等高级 Policy Provider；
- 完整JSON Schema 2020-12关键字集（当前为Runtime安全子集）。

## 构建

```bash
JAVA_HOME=/Library/Java/JavaVirtualMachines/jdk-21.jdk/Contents/Home \
PATH=/Library/Java/JavaVirtualMachines/jdk-21.jdk/Contents/Home/bin:$PATH \
mvn clean test
```

## 本地基础设施

控制面使用 MySQL 存储配置资产并负责执行 Flyway 迁移，控制面、运行面和 Worker 使用 Redis 承载缓存及运行态协调信息。

默认开发地址通过环境变量提供，不在版本库中保存密码：

```bash
export TPIP_MYSQL_HOST=127.0.0.1
export TPIP_MYSQL_PORT=3306
export TPIP_MYSQL_DATABASE=tpip_platform
export TPIP_MYSQL_USERNAME=root
export TPIP_MYSQL_PASSWORD='your-local-password'
export TPIP_REDIS_HOST=127.0.0.1
export TPIP_REDIS_PORT=6379
```

本地也可以复制 `config/application-local.example.yml` 为被 Git 忽略的
`config/application-local.yml`，然后使用 `--spring.profiles.active=local` 启动。

基础设施职责边界：

- `tpip-control-plane-app`：MySQL、Flyway、Redis。
- `tpip-runtime-app`：Redis，不直接访问配置数据库。
- `tpip-worker-app`：Redis；数据库任务存储在用例落地前不提前引入。
- `database/migration`：迁移脚本唯一来源，由控制面打包进 `classpath:db/migration`。

数据库连接会把 MySQL Session 时区固定为 UTC，API 时间字段统一使用 ISO-8601 UTC
格式，展示层再按用户时区转换，避免不同部署节点产生重复时区偏移。

## 当前控制面能力

```text
POST /control/v1/providers
GET  /control/v1/providers
GET  /control/v1/providers/{id}
PUT  /control/v1/providers/{id}

POST /control/v1/credentials
GET  /control/v1/credentials
GET  /control/v1/credentials/{id}
PUT  /control/v1/credentials/{id}

POST /control/v1/operations
GET  /control/v1/operations
GET  /control/v1/operations/{id}
PUT  /control/v1/operations/{id}

POST /control/v1/domains
GET  /control/v1/domains
GET  /control/v1/domains/{id}
PUT  /control/v1/domains/{id}

POST /control/v1/capabilities
GET  /control/v1/capabilities
GET  /control/v1/capabilities/{id}
PUT  /control/v1/capabilities/{id}

POST /control/v1/contracts
GET  /control/v1/contracts
GET  /control/v1/contracts/{id}
PUT  /control/v1/contracts/{id}
POST /control/v1/contracts/{id}/versions
GET  /control/v1/contracts/{id}/versions
GET  /control/v1/contracts/{id}/versions/{versionId}
POST /control/v1/contracts/{id}/versions/{versionId}:publish

POST /control/v1/bindings
GET  /control/v1/bindings
GET  /control/v1/bindings/{id}
PUT  /control/v1/bindings/{id}
POST /control/v1/bindings/{id}/versions
GET  /control/v1/bindings/{id}/versions
GET  /control/v1/bindings/{id}/versions/{versionId}
POST /control/v1/bindings/{id}/versions/{versionId}:publish
GET  /control/v1/bindings/{id}/versions/{versionId}/bundle-preview

POST /control/v1/workspaces
GET  /control/v1/workspaces
GET  /control/v1/workspaces/{id}
POST /control/v1/workspaces/{id}/assets/binding-version
GET  /control/v1/workspaces/{id}/assets
GET  /control/v1/workspaces/{id}/verifications
POST /control/v1/workspaces/{id}:verify
GET  /control/v1/workspaces/{id}/verification-jobs
GET  /control/v1/verification-jobs/{id}
GET  /control/v1/verification-jobs/{id}/checks
POST /control/v1/verification-jobs/{id}:retry
POST /control/v1/workspaces/{id}:submit-review
POST /control/v1/workspaces/{id}/approvals
GET  /control/v1/workspaces/{id}/approvals
POST /control/v1/workspaces/{id}/bundles
GET  /control/v1/workspaces/{id}/bundles

POST /control/v1/fixture-suites
GET  /control/v1/fixture-suites?bindingId={bindingId}
GET  /control/v1/fixture-suites/{id}
POST /control/v1/fixture-suites/{id}/versions
GET  /control/v1/fixture-suites/{id}/versions
GET  /control/v1/fixture-suites/{id}/versions/{versionId}
POST /control/v1/fixture-suites/{id}/versions/{versionId}:publish

GET  /control/v1/bundles/{id}
POST /control/v1/bundles/{id}:publish
GET  /artifacts/v1/bundles/{bundleCode}/versions/{bundleVersion}

POST /control/v1/deployments
GET  /control/v1/deployments/{id}
GET  /control/v1/deployments?operationCode={code}&environmentCode={env}
POST /control/v1/deployments/{id}:preheat
POST /control/v1/deployments/{id}:activate
POST /control/v1/deployments/{id}:traffic
POST /control/v1/deployments/{id}:rollback
POST /control/v1/deployments/{id}:evaluate-health
GET  /control/v1/deployments/{id}/health-evaluations
GET  /control/v1/deployments/{id}/health-alerts
POST /control/v1/deployments/{id}/health-alerts/{alertId}:acknowledge
GET  /runtime-config/v1/routes/{operationCode}/environments/{environmentCode}

GET  /internal/v1/deployment-health/candidates
POST /internal/v1/deployment-health/{id}:evaluate

POST /internal/v1/notification-deliveries:claim
POST /internal/v1/notification-deliveries/{id}:delivered
POST /internal/v1/notification-deliveries/{id}:failed
GET  /control/v1/notification-deliveries?status=DEAD_LETTER
POST /control/v1/notification-deliveries/{id}:replay
POST /control/v1/notification-deliveries:batch-replay
GET  /control/v1/notification-operations/summary
GET  /control/v1/notification-operations/evaluations
GET  /control/v1/notification-operations/alerts
POST /control/v1/notification-operations/alerts/{alertId}:acknowledge
POST /control/v1/notification-operations-governance/policy-versions
GET  /control/v1/notification-operations-governance/policy-versions
POST /control/v1/notification-operations-governance/policy-versions/{id}:publish
POST /control/v1/notification-operations-governance/maintenance-windows
GET  /control/v1/notification-operations-governance/maintenance-windows
POST /control/v1/notification-operations-governance/maintenance-windows/{id}:cancel
GET  /control/v1/notification-circuits/{assetType}/{assetId}
POST /control/v1/notification-attempt-archives
GET  /control/v1/notification-attempt-archives
GET  /control/v1/notification-attempt-archives/{id}
GET  /control/v1/notification-attempt-archives/storage-health
POST /control/v1/notification-attempt-archives/{id}:retry
POST /control/v1/notification-attempt-archives/{id}:verify
POST /control/v1/notification-attempt-archives/{id}:audit
GET  /control/v1/notification-attempt-archives/{id}/verifications
POST /control/v1/notification-attempt-archives/{id}:legal-hold
POST /control/v1/notification-attempt-archives/{id}:purge

POST /control/v1/notification-channels
GET  /control/v1/notification-channels
POST /control/v1/notification-channels/{id}/versions
POST /control/v1/notification-channels/{id}/versions/{versionId}:publish
POST /control/v1/notification-routes
GET  /control/v1/notification-routes
POST /control/v1/notification-routes/{id}/versions
POST /control/v1/notification-routes/{id}/versions/{versionId}:publish
POST /control/v1/notification-channels/{id}:status
POST /control/v1/notification-routes/{id}:status
GET  /control/v1/notification-routing-failures
POST /control/v1/notification-routing-failures/{eventId}:reroute
POST /control/v1/notification-templates
GET  /control/v1/notification-templates
POST /control/v1/notification-templates/{id}/versions
POST /control/v1/notification-templates/{id}/versions/{versionId}:publish
POST /control/v1/notification-templates/{id}:status
GET  /control/v1/notification-provider-capabilities
POST /control/v1/endpoints/{id}:probe
GET  /control/v1/endpoints/{id}/probes

POST /control/v1/drift-governance-policies
GET  /control/v1/drift-governance-policies
GET  /control/v1/drift-governance-policies/{policyId}
POST /control/v1/drift-governance-policies/{policyId}/versions
GET  /control/v1/drift-governance-policies/{policyId}/versions
POST /control/v1/drift-governance-policies/{policyId}/versions/{versionId}:publish
POST /control/v1/drift-governance-policies/{policyId}:activate
POST /control/v1/drift-governance-policies/{policyId}:pause
GET  /control/v1/drift-governance-policies:resolve?workspaceId={workspaceId}
GET  /control/v1/verification-drift-workbench/governance-evaluations?workspaceId={workspaceId}
POST /control/v1/verification-drift-workbench/governance-executions:materialize
GET  /control/v1/verification-drift-workbench/governance-executions?workspaceId={workspaceId}
GET  /control/v1/verification-drift-workbench/governance-executions/{executionId}
POST /control/v1/verification-drift-workbench/governance-reminder-batches
POST /control/v1/verification-drift-workbench/governance-reminder-batches/{batchId}:approve
POST /control/v1/verification-drift-workbench/governance-reminder-batches/{batchId}:dispatch
POST /control/v1/verification-drift-workbench/governance-reminder-batches/{batchId}:cancel
POST /control/v1/verification-drift-workbench/governance-reminder-batches/{batchId}:replace
GET  /control/v1/verification-drift-workbench/governance-reminder-batches?workspaceId={workspaceId}
GET  /control/v1/verification-drift-workbench/governance-reminder-batches/{batchId}
GET  /control/v1/verification-drift-workbench/governance-reminder-batches/{batchId}/diff
GET  /control/v1/verification-drift-workbench/governance-reminder-batches/{batchId}/delivery-status
GET  /control/v1/verification-drift-workbench/governance-reminder-metrics?workspaceId={workspaceId}
POST /control/v1/verification-drift-workbench/governance-reviews:assign
POST /control/v1/verification-drift-workbench/governance-reviews:acknowledge
POST /control/v1/verification-drift-workbench/governance-reviews:dispose
GET  /control/v1/verification-drift-workbench/governance-operations/{commandKey}
GET  /control/v1/verification-drift-workbench/governance-metrics?workspaceId={workspaceId}
GET  /control/v1/verification-drift-workbench/governance-policy-impact?workspaceId={workspaceId}&candidatePolicyId={policyId}&candidateVersionId={versionId}
POST /control/v1/verification-drift-workbench/governance-policy-impact-snapshots
GET  /control/v1/verification-drift-workbench/governance-policy-impact-snapshots/{snapshotId}
POST /control/v1/verification-drift-workbench/global-governance-policy-impact-snapshots
GET  /control/v1/verification-drift-workbench/global-governance-policy-impact-snapshots/{snapshotId}
GET  /control/v1/verification-drift-workbench/global-governance-policy-impact-snapshots/{snapshotId}/workspace-snapshots
POST /control/v1/verification-drift-workbench/global-governance-policy-impact-jobs
GET  /control/v1/verification-drift-workbench/global-governance-policy-impact-jobs/{jobId}
GET  /control/v1/verification-drift-workbench/global-governance-policy-impact-jobs/{jobId}/items
POST /control/v1/verification-drift-workbench/global-governance-policy-impact-jobs/{jobId}:run-batch
POST /control/v1/verification-drift-workbench/global-governance-policy-impact-jobs/{jobId}:retry-failed
POST /control/v1/verification-drift-workbench/global-governance-policy-impact-jobs/{jobId}:seal

POST /control/v1/mappings
GET  /control/v1/mappings
GET  /control/v1/mappings/{id}
PUT  /control/v1/mappings/{id}
POST /control/v1/mappings/{id}/versions
GET  /control/v1/mappings/{id}/versions
GET  /control/v1/mappings/{id}/versions/{versionId}
POST /control/v1/mappings/{id}/versions/{versionId}:publish
POST /control/v1/mappings/{id}/versions/{versionId}:test

POST /control/v1/policy-types
GET  /control/v1/policy-types
GET  /control/v1/policy-types/{id}
POST /control/v1/policy-types/{id}:status

POST /control/v1/policies
GET  /control/v1/policies
GET  /control/v1/policies/{id}
PUT  /control/v1/policies/{id}
POST /control/v1/policies/{id}/versions
GET  /control/v1/policies/{id}/versions
GET  /control/v1/policies/{id}/versions/{versionId}
GET  /control/v1/policies/{id}/versions/{versionId}/plan
POST /control/v1/policies/{id}/versions/{versionId}:publish

POST /control/v1/provider-contracts
GET  /control/v1/provider-contracts
GET  /control/v1/provider-contracts/{id}
PUT  /control/v1/provider-contracts/{id}
POST /control/v1/provider-contracts/{id}/versions
GET  /control/v1/provider-contracts/{id}/versions
GET  /control/v1/provider-contracts/{id}/versions/{versionId}
POST /control/v1/provider-contracts/{id}/versions/{versionId}:publish

POST /control/v1/endpoints
GET  /control/v1/endpoints
GET  /control/v1/endpoints/{id}
POST /control/v1/endpoints/{id}:publish
```

所有命令接口要求 `X-Operator` 请求头。Provider 和 ProviderContract 稳定身份使用
`rowVersion` 乐观锁；ContractVersion 创建后不提供修改接口，发布动作会写入审计事件。

Endpoint 使用 `endpointCode + environmentCode + revisionNo` 标识不可变修订。同一环境下
再次创建相同 Endpoint 编码会自动生成下一修订；默认列表只返回最新修订。Endpoint 只保存
`credentialRefId`，不会保存或返回 Secret 原文。

通知投递尝试归档与安全清理规则见
[`docs/notification-attempt-verified-archive-v0.12.md`](docs/notification-attempt-verified-archive-v0.12.md)。
本地文件归档仅用于开发验证，清理开关默认关闭；生产环境需要替换为具备WORM/Object Lock
能力的对象存储，并补齐OIDC/RBAC与双人审批。

自动归档、跨实例租约和可恢复性抽检规则见
[`docs/notification-attempt-archive-automation-v0.13.md`](docs/notification-attempt-archive-automation-v0.13.md)。
自动化总开关默认关闭，并且调度器永远不会自动调用在线证据清理。

S3兼容生产归档、对象版本绑定和Object Lock能力门禁见
[`docs/notification-attempt-s3-archive-storage-v0.14.md`](docs/notification-attempt-s3-archive-storage-v0.14.md)。
默认仍使用本地文件适配器；只有显式配置`storage-provider=S3`才会创建S3客户端。
本地MinIO真实联调当前登记为`DEFERRED / ON_DEMAND`，不阻塞后续本地核心能力建设；
在自动归档启用、对象存储选型或生产验收前按需恢复。

OIDC/JWT与RBAC当前同样登记为`DEFERRED / ON_DEMAND`。系统保持本地单机、单用户使用，
不得暴露到不可信网络；出现共享环境、外部Auth、真实Secret或多人职责分离需求时恢复实施。
首期 UI 不包含登录、用户管理和角色管理，前端只保留可插拔认证扩展点，且仅允许本机访问。
具体触发条件见
[`docs/deferred-authentication-authorization-backlog.md`](docs/deferred-authentication-authorization-backlog.md)。

Global 影响任务面向 UI 的稳定只读查询模型、组合过滤分页、详情、Workspace 结构化影响对比、审计时间线和
`allowedActions` 契约见
[`docs/verification-global-impact-ui-query-model-v1.4.md`](docs/verification-global-impact-ui-query-model-v1.4.md)。
v1.4 不新增数据库迁移，也不改变既有命令和 Worker API；下一阶段开始 UI 前执行提醒门禁。

UI 阶段已经开始。已批准并落地 Vue 3 + TypeScript + Vite + Element Plus 技术栈，首个纵向切片包含本地无登录
应用外壳、全局影响任务列表、组合筛选分页、任务详情、Workspace 影响和审计时间线。工程说明见
[`tpip-ui/README.md`](tpip-ui/README.md)，选型决策见
[`docs/adr/ADR-001-ui-technology-stack.md`](docs/adr/ADR-001-ui-technology-stack.md)。
UI 工程 v0.2 已完成 Element Plus 按需解析、vendor 分包和统一异步状态组件，主业务入口约 4 KB，最大 JS chunk
约 380 KB，严格类型检查和 7 个单元测试通过。验收基线见
[`docs/tpip-ui-engineering-v0.2.md`](docs/tpip-ui-engineering-v0.2.md)。
UI v0.3 新增服务端全量影响摘要、风险与执行状态可视化、Control Plane 在线状态和统一刷新；摘要不是分页推断。
查询契约见 [`docs/verification-global-impact-ui-summary-query-model-v1.5.md`](docs/verification-global-impact-ui-summary-query-model-v1.5.md)，
UI 说明见 [`docs/tpip-ui-impact-overview-v0.3.md`](docs/tpip-ui-impact-overview-v0.3.md)。
UI v0.4 已受控接入优先级调整、取消、失败重试和封板，统一携带 rowVersion 与审计 Operator；命令冲突使用
HTTP 409，客户端刷新但不自动重放。详见 [`docs/tpip-ui-controlled-commands-v0.4.md`](docs/tpip-ui-controlled-commands-v0.4.md)
和 [`docs/verification-global-impact-command-conflict-v1.6.md`](docs/verification-global-impact-command-conflict-v1.6.md)。
UI v0.5 已新增封板快照只读资产页，服务端通过独立 View API 解析结构化 Workspace 影响，前端不读取原始
`impactDocument`；受控命令成功后会自动定位本次审计事件。详见
[`docs/tpip-ui-sealed-snapshot-v0.5.md`](docs/tpip-ui-sealed-snapshot-v0.5.md) 和
[`docs/verification-global-impact-snapshot-ui-query-model-v1.7.md`](docs/verification-global-impact-snapshot-ui-query-model-v1.7.md)。
可重复执行的 READY → SEALED 本地验收脚本和真实证据见
[`e2e/global-impact-seal/README.md`](e2e/global-impact-seal/README.md)。
UI v0.6 已增加全局影响任务创建页，支持 Global 策略、DRAFT/PUBLISHED 候选版本和受控 TTL 配置；
创建只生成 PENDING 任务，不自动执行 Worker 或封板。详见
[`docs/tpip-ui-global-impact-job-create-v0.6.md`](docs/tpip-ui-global-impact-job-create-v0.6.md)。
UI v0.7 已增强任务运行引导、临期分级提示、Workspace 服务端筛选与失败诊断，并增加由服务端 capability 驱动的
READY 封板前检查。详见
[`docs/tpip-ui-global-impact-operations-v0.7.md`](docs/tpip-ui-global-impact-operations-v0.7.md)。
UI v0.8 已新增全局运营态势页，统一展示停滞任务、SLO 分级、优先级和恢复建议，并可进入任务详情处置；
服务端提供独立稳定的只读运营 View API。详见
[`docs/tpip-ui-global-impact-operations-v0.8.md`](docs/tpip-ui-global-impact-operations-v0.8.md) 和
[`docs/verification-global-impact-operations-query-model-v1.8.md`](docs/verification-global-impact-operations-query-model-v1.8.md)。
UI v0.9 已新增治理策略资产列表、策略详情和不可变版本详情，并将影响任务、运营态势和封板快照中的候选策略
打通到策略版本。详见
[`docs/tpip-ui-governance-policy-assets-v0.9.md`](docs/tpip-ui-governance-policy-assets-v0.9.md) 和
[`docs/verification-governance-policy-asset-query-model-v1.9.md`](docs/verification-governance-policy-asset-query-model-v1.9.md)。
UI v0.10 已新增 Workspace 资产列表和详情，统一追溯有效策略、验证基线、漂移报告及全局影响证据。详见
[`docs/tpip-ui-workspace-assets-v0.10.md`](docs/tpip-ui-workspace-assets-v0.10.md) 和
[`docs/verification-workspace-asset-query-model-v2.0.md`](docs/verification-workspace-asset-query-model-v2.0.md)。
UI v0.11 已新增漂移治理工作台，支持组合筛选、运营摘要、同 Workspace 批量选择，以及负责人分派、确认、接受与
驳回的强制 Dry Run 流程；命令统一携带 `rowVersion`、独立幂等键和本地审计 Operator。详见
[`docs/tpip-ui-drift-governance-workbench-v0.11.md`](docs/tpip-ui-drift-governance-workbench-v0.11.md)。
UI v0.12 已新增治理操作证据页，支持按 Command Key 回查请求 checksum、请求快照、逐项拒绝原因、版本变化、
幂等标识和后继基线；Dry Run 与正式执行均可进入对应证据，Workspace 详情可直接进入限定范围的漂移治理。
详见 [`docs/tpip-ui-drift-operation-evidence-v0.12.md`](docs/tpip-ui-drift-operation-evidence-v0.12.md)。
UI v0.13 已新增 Workspace 治理运营度量，展示策略 SLA、待办与超期、负责人负载、处置效率、命令质量、UTC
日趋势和漂移聚合簇，并可钻取到工作台组合筛选结果。详见
[`docs/tpip-ui-drift-governance-metrics-v0.13.md`](docs/tpip-ui-drift-governance-metrics-v0.13.md)。
UI v0.14 已新增治理评估与执行资产页，展示有效策略快照、逐漂移项抑制证据、提醒候选，以及已冻结执行账本的
预算、聚合键和 checksum；当前保持完全只读，不物化账本或创建通知。详见
[`docs/tpip-ui-drift-governance-evaluation-assets-v0.14.md`](docs/tpip-ui-drift-governance-evaluation-assets-v0.14.md)。
UI v0.15 已新增提醒批次运营资产，展示批次生命周期、替代成员差异、Outbox/渠道投递关联、积压与终态成功率；
当前保持完全只读，不执行批准、提交、取消、替代或发送。详见
[`docs/tpip-ui-drift-reminder-batch-assets-v0.15.md`](docs/tpip-ui-drift-reminder-batch-assets-v0.15.md)。
UI v0.16 已完成治理工作台浏览器级可回滚验收门禁：隔离夹具覆盖超期候选、冻结账本、DRAFT/取消/替代/已提交
批次及模拟投递，真实验证评估展开、SLA 度量、替代差异和渠道终态；验收后按 manifest 精确回滚并二次确认无残留。
详见 [`docs/tpip-ui-browser-acceptance-fixture-v0.16.md`](docs/tpip-ui-browser-acceptance-fixture-v0.16.md) 和
[`e2e/ui-governance-workbench/README.md`](e2e/ui-governance-workbench/README.md)。
UI v0.17 已将治理评估与提醒批次从只读资产页扩展为受控命令工作台：支持冻结执行账本、读取服务端到期候选、
创建 DRAFT、批准、取消、原子替代和显式确认提交；所有状态转换携带 Row Version，由服务端重新校验并形成审计，
提交才在同一事务中产生 Outbox。详见
[`docs/tpip-ui-drift-governance-commands-v0.17.md`](docs/tpip-ui-drift-governance-commands-v0.17.md)。
UI v0.18 已在提醒批次详情中建立不可变命令审计时间线：创建、批准、取消、替代血缘和提交事件记录操作者、
Row Version、治理原因、关联批次与 Outbox；旧事件可兼容读取，无审计记录时明确显示空证据，不从可变业务状态
伪造历史。详见
[`docs/tpip-ui-reminder-batch-audit-timeline-v0.18.md`](docs/tpip-ui-reminder-batch-audit-timeline-v0.18.md)。
UI v0.19 已将提醒批次命令链固化为可回滚 Playwright 门禁，自动验证审计事件顺序、Row Version 连续性、
事件 UUID 唯一性、替代双向血缘、治理原因和 Outbox/投递投影一致性；成功或失败均执行隔离数据清理和二次确认。
详见 [`docs/tpip-ui-reminder-audit-automation-gate-v0.19.md`](docs/tpip-ui-reminder-audit-automation-gate-v0.19.md)。
UI v0.20 已将自动门禁升级为可归档证据生产器：成功时固化业务资产、完整审计链和 SHA-256 checksum，失败时记录
精确阶段与 Trace 路径；JSON/Markdown 报告在数据库清理和无残留复核后统一生成，可直接进入 CI 制品、发布封板或
PCS/EA 证据关联。详见
[`docs/tpip-ui-governance-gate-report-v0.20.md`](docs/tpip-ui-governance-gate-report-v0.20.md)。
交付治理 v0.21 已建立本地发布封板与证据索引：全量 Java/UI 测试、三个应用 Jar、UI `dist`、数据库迁移和 v0.20
治理门禁被复制到独立发布目录并形成双层 SHA-256 校验；独立校验器会重新读取全部制品和审计链，拒绝缺失、篡改或
未完成清理的发布。当前无正式数字签名，状态明确标记为 `UNSIGNED`。详见
[`docs/tpip-release-seal-evidence-v0.21.md`](docs/tpip-release-seal-evidence-v0.21.md)。

UI v0.22 已将产品主入口切换为第三方接入配置中心，并交付第一阶段的 Provider、CredentialRef、
ProviderContract 稳定身份和 Endpoint Revision 配置，以及 Endpoint 探测和发布。CredentialRef 只登记
`env://TPIP_SECRET_*` 引用，浏览器不保存或回显 Secret。既有治理能力保留在“运营治理”二级菜单；Canonical、
Contract Version、JSONPath Mapping、Policy DSL、Binding 和验证发布向导按真实接入依赖继续建设。详见
[`docs/tpip-integration-configuration-workbench-v0.22.md`](docs/tpip-integration-configuration-workbench-v0.22.md)。

v0.22 第二阶段已补齐业务标准接口工作台：Domain、Capability、Operation、Canonical Contract 及 JSON Schema
2020-12 不可变版本均可配置，DRAFT Version 可显式发布并保留服务端生成的 checksum。

v0.22 第三阶段已交付 ProviderContractVersion、Binding 和 JSONPath Mapping 工作台：第三方报文 Schema 可版本化
发布，出站请求和入站响应映射自动绑定匹配方向的已发布 Schema，支持多规则编辑、真实 Mapping Engine 样例测试、
诊断输出、编译计划 checksum 和显式发布。下一阶段进入 Policy DSL 与 BindingVersion 依赖闭包。

v0.22 第四阶段已交付 Policy Type Registry、受控 Policy DSL 和 BindingVersion 执行闭包：Policy 创建时由类型化
编译器校验并可查看 Compiled Plan；BindingVersion 自动冻结已发布的 Canonical、Provider、Endpoint、双向 Mapping
和可选 Policy，并支持发布前 Bundle Manifest 预览。第五阶段已交付 FixtureSuite UI 资产化，包括多 Case、
FAP 受控断言、MAPPING/REMOTE_CALL 模式、不可变版本与发布。第六阶段已交付 Workspace 创建、BindingVersion
装配、FixtureSuiteVersion 选择、服务端验证、Check 证据和失败重试。第七阶段已交付评审、风险分级审批、
Bundle 编译、Manifest 查看和发布。第八阶段已交付 Deployment 创建、真实预热、激活、灰度扩量、回滚、
ACTIVE Route 与 Runtime Canonical Request 调用工作台。第九阶段已交付基于服务端事实的八阶段接入向导，
支持缺口定位、专家页面跳转和仅保存非敏感资产 ID 的本地断点恢复。

本地第三方接入端到端验收的标准场景、资产顺序、故障用例和证据要求见
[`docs/local-end-to-end-acceptance-plan.md`](docs/local-end-to-end-acceptance-plan.md)。
面向人工学习、完全通过当前 UI 配置的客户资料查询实例见
[`docs/tpip-customer-profile-lookup-ui-tutorial.md`](docs/tpip-customer-profile-lookup-ui-tutorial.md)。
可重复执行的 Mock Provider、资产引导、调用断言、回滚脚本和本次验收证据见
[`e2e/customer-lookup/README.md`](e2e/customer-lookup/README.md)。

CredentialRef 使用 `credentialCode + environmentCode` 标识稳定身份，只保存 Secret Manager
引用 URI 和不含敏感值的元数据。引用 URI 仅允许受控 Secret 协议，不接受 HTTP、文件路径、
查询参数或片段；元数据会递归拒绝 password、token、apiKey、clientSecret、privateKey 等敏感字段。

## 当前运行面能力

```text
POST /integration/v1/operations/{operationCode}:invoke
GET  /runtime/v1/bundles
POST /runtime/v1/deployments:preheat
GET  /actuator/health
GET  /actuator/metrics
GET  /actuator/prometheus
```

控制面维护 `operationCode + environmentCode` 的动态 Deployment 路由快照，Runtime 通过控制面 API
获取路由并只通过制品下载 API 读取已发布 Bundle，不连接设计态 MySQL。预热成功并达到实例法定人数后
Deployment 才能进入 READY；激活和扩量会原子保证活动流量总和为 100%。灰度选择使用 requestId
进行确定性哈希，同一次请求重试始终绑定同一 Deployment。缩量必须走显式回滚，回滚会创建新的部署
动作指向上一不可变 Bundle，不改写历史 Bundle。

Bundle Resolver 会依次校验下载响应 checksum、Deployment 固定 checksum、Manifest 内容 checksum、
资产身份、请求/响应 Mapping 完整性及 Runtime 兼容区间。Bundle 与动态路由快照分别进入进程内 L1
缓存；刷新失败时只在各自 `max-stale` 时间内使用 Last Known Good，并在状态接口中标记为
`STALE_FALLBACK`。

调用入口在选定 Bundle 后按固定顺序执行：Canonical Request 校验、请求 Policy、请求 Mapping、
Provider Request 校验、Transport Policy、HTTP 调用、Provider Response 校验、响应 Mapping、
响应 Policy 和 Canonical Response 校验。请求或映射不合规时不会发起第三方调用；第三方非 2xx、
超时、策略失败和契约失败会转换为稳定的 `InvocationResult.code`。当前内置
`builtin.transport.inject@1.x` 和 `builtin.auth.api-key@1.x`，可以通过受控插值注入请求头，
或从 Secret Reference 解析 API Key，不允许运行任意脚本。

Deployment 预热除下载和校验 Bundle 外，还会提前检查 Runtime JSON Schema Profile、Endpoint、
Policy Provider 和 Secret Reference 可用性。任一实例不兼容都会返回 422 并进入预热失败证据，
不能把不具备执行条件的 Bundle 激活。开发环境可使用 `env://TPIP_SECRET_*` 引用；Runtime 仅允许
读取此前缀的环境变量，Bundle、数据库、API 响应和诊断均不保存 Secret 明文。

Runtime 使用 Micrometer 输出调用总量、端到端耗时、阶段耗时和第三方 HTTP 状态类别，并通过
`tpip.audit.runtime` 输出不含报文及 Secret 的结构化审计。Deployment 健康评估根据窗口样本数、
失败数和 P95 延迟重新计算门禁；不健康 Canary 不做不可审计的静默缩流，而是自动创建回滚
Deployment。完整指标、审计和门禁语义见 `docs/runtime-observability-governance-v0.1.md`。

外部配置示例：

```yaml
tpip:
  runtime:
    control-plane-base-uri: http://tpip-control-plane:8080
    environment-code: prod
    runtime-version: 0.1.0
    cache-ttl: 5m
    max-stale: 24h
    max-artifact-bytes: 10485760
    route-ttl: 5s
    route-max-stale: 5m
    max-provider-response-bytes: 2097152
    instance-id: tpip-runtime-prod-01
    audit-enabled: true
```

控制面健康门禁默认配置：

```yaml
tpip:
  deployment:
    health-minimum-samples: 100
    health-maximum-error-rate: 5.00
    health-maximum-p95-latency-ms: 2000
    health-auto-rollback-enabled: true
    health-consecutive-unhealthy-windows: 2
    health-critical-error-rate: 20.00
    health-critical-p95-latency-ms: 5000
```

自动化 Worker 默认关闭。启用时，控制面和 Worker 必须从 Secret Manager 注入相同的
`TPIP_HEALTH_AUTOMATION_TOKEN`：

```yaml
tpip:
  health-worker:
    enabled: true
    control-plane-base-uri: http://tpip-control-plane:8080
    prometheus-base-uri: http://prometheus:9090
    poll-interval: 30s
    evaluation-window: 5m
    cooldown: 1m
    lock-ttl: 10m
```

Worker 不访问 MySQL。它通过受保护的内部接口发现 Canary，按 UTC 对齐窗口查询 Prometheus，
使用 Redis 锁避免多实例重复提交；控制面 V6 唯一约束负责最终幂等。完整说明见
`docs/health-automation-worker-v0.1.md`。

普通异常默认连续两个相邻窗口后才执行自动回滚；错误率达到 20% 或 P95 达到 5000ms 时立即保护。
异常、人工确认和恢复会生成告警并与通知 Outbox 在同一事务提交，完整语义见
`docs/health-alert-governance-v0.1.md`。

通知 Dispatcher 默认关闭。渠道与路由先在控制面创建不可变版本并发布；Outbox 首次被领取时按
已发布路由物化 Delivery，并冻结 `channelVersionId/providerType/endpointUri/secretRef`。渠道版本可绑定
已发布通知模板；控制面在物化时校验变量 Schema、渲染并冻结 `templateVersionId/contentType/messagePayload`，Worker 只执行
该快照，不连接 MySQL，也不读取设计态配置。每个稳定 `channelCode` 独立重试、死信和重放；Webhook
使用事件与渠道组合的稳定 `Idempotency-Key` 提供 at-least-once 投递，生产地址默认强制 HTTPS。
Worker 在 8082 管理端口暴露投递计数和耗时指标。基础状态机见
`docs/notification-outbox-dispatcher-v0.1.md`，过渡模型见 `docs/notification-channel-delivery-v0.2.md`，
资产化模型见 `docs/notification-routing-assets-v0.3.md`，环境与运营治理见
`docs/notification-routing-governance-v0.4.md`，模板与内容冻结见
`docs/notification-template-governance-v0.5.md`，企业微信、钉钉 Provider、签名、错误归一与分布式限流见
`docs/notification-provider-execution-governance-v0.6.md`，通知渠道复用 ProviderEndpoint、执行快照与无凭证
连通性预检见 `docs/notification-endpoint-binding-governance-v0.7.md`，错误分类、Retry-After、死信事实和
分布式端点熔断见 `docs/notification-runtime-reliability-governance-v0.8.md`。
不可变投递尝试证据、运营汇总、批量死信重放和单许可半开恢复见
`docs/notification-operations-governance-v0.9.md`。固定窗口后台评估、环境级幂等、可确认/恢复告警和
归档前不删除证据的保留边界见 `docs/notification-operations-automation-v0.10.md`。
环境级不可变 SLO 策略、维护窗口、告警抑制、超时升级和重复提醒见
`docs/notification-operations-service-level-governance-v0.11.md`。

本地开发 Secret 示例：

```bash
export TPIP_SECRET_PROVIDER_A_API_KEY='local-development-value'
```

对应 CredentialRef/Policy 只保存 `env://TPIP_SECRET_PROVIDER_A_API_KEY`。生产环境应替换为 Vault 或
云 Secret Manager 适配器，不建议使用容器环境变量长期承载生产密钥。

CanonicalOperation 是业务系统调用的稳定标准操作，不包含供应商或渠道语义；创建时必须关联
ACTIVE Capability。IntegrationBinding 将一个 ACTIVE CanonicalOperation 连接到一个 ACTIVE
ProviderContract，稳定身份和两端引用创建后不可修改，元数据更新使用 `rowVersion` 乐观锁。

CanonicalContract 按 REQUEST、RESPONSE、ERROR、EVENT 区分标准契约类型。ContractVersion
使用 JSON Schema 2020-12，内容规范化后计算 SHA-256；版本创建后不可修改，只允许从
DRAFT 显式发布为 PUBLISHED。

IntegrationMapping 按 Binding 和方向建立稳定身份，同一个 Binding 的同一方向只能有一个
Mapping。MappingVersion 必须引用当前 Binding 所属的已发布 CanonicalContractVersion 和
ProviderContractVersion；规则创建时完成规范化和 JSONPath 编译校验，版本创建后不可修改，
只允许从 DRAFT 显式发布。v0.1 目标路径采用确定性对象属性和数组索引语法，不允许递归下降、
过滤表达式或任意脚本。Fixture 接口可以对 DRAFT 或 PUBLISHED 版本执行不落库的样例转换，
返回转换结果、逐规则诊断和编译计划摘要。

Policy Type Registry 以 `policyTypeCode@semanticVersion` 作为精确实现身份，登记允许挂载阶段、
受控配置 Schema、运行时兼容范围、安全等级、副作用和幂等要求。Policy DSL 不执行 Groovy 或
其他任意脚本；`tpip.policy/v1alpha1` 只接受固定阶段、注册类型、受控条件表达式和
`${canonical.*}`、`${provider.*}`、`${context.*}`、`${transport.*}`、`${outcome.*}` 命名空间。
敏感凭证只能通过 `secret://` 引用传递，不能在策略配置中保存 Secret 原文；CRITICAL 或
SENSITIVE 类型不能配置失败后继续。版本创建时完成规范化、Schema 校验与编译，发布后不可变，
`/plan` 可查看确定性的编译计划和 SHA-256 校验和。

BindingVersion 是设计态资产进入运行态前的冻结边界。v0.1 要求请求和响应 ContractVersion、
ProviderContractVersion、Endpoint、请求/响应 MappingVersion 均已发布且属于同一个 Binding；
PolicyVersion 可选，但引用时必须已编译并发布。Mapping 中保存的 Schema Reference 必须与本次
冻结的 ContractVersion 精确一致，幂等等级从 CanonicalOperation 派生。BindingVersion 发布前
会再次校验依赖闭包，发布后不可修改。Bundle Preview 只能从已发布 BindingVersion 生成，产物
包含契约快照、Mapping IR、Policy IR、Endpoint 快照、Secret Reference 和确定性 checksum，
不包含明文 Secret，也不要求 Runtime 读取设计态数据库。

ConfigurationWorkspace v0.2 使用 `DRAFT -> VERIFIED -> IN_REVIEW -> APPROVED -> COMPILED`
状态机。工作区必须引用一个已发布 BindingVersion；FixtureSuite 按 Binding 建立稳定身份，
FixtureSuiteVersion 是内容带 SHA-256 的不可变快照，发布后才能作为验证输入。Workspace 验证由
服务端执行依赖闭包、Bundle Preview、Endpoint Probe 和全部 Mapping Fixture，逐项保存 Check 与
证据；API 不再接受客户端传入 PASSED、计数或证据。只有带实际 Check 的 PASSED Job 才能提交评审；Owner 不能
审批自己的工作区；HIGH/CRITICAL 风险要求 RELEASE 和 SECURITY 两个阶段由不同审批人完成。
Mapping Fixture 支持 Fixture Assertion Profile 1.0 的成功状态、诊断码、确定性 JSONPath、受控 JSON Schema
和只读 Policy 表达式断言，逐条保存实际值与结论；历史整份报文比较继续兼容。显式 REMOTE_CALL Fixture
通过候选 Bundle 的完整 Runtime Pipeline 执行 HTTP Status、Header 和 Provider Body 断言；功能默认关闭，
只允许 IDEMPOTENT BindingVersion，并受环境/主机/端口白名单、HTTPS、超时、大小、调用数量和证据脱敏门禁约束。
FixtureSuite 回归基线与漂移检测 v0.1 可从一次 `PASSED FULL` 服务端验证创建不可变脱敏基线，手动执行
`REGRESSION` VerificationRun，并生成 `NO_DRIFT / DRIFTED` 报告。设计、API 和安全边界见
`docs/fixture-regression-baseline-drift-v0.1.md`。
可选的 RegressionPolicy 定时回归 v0.1 提供不可变策略版本、显式发布/激活、数据库租约、失败退避、阈值
自动暂停，并将漂移和暂停事件写入 Notification Outbox；全局默认关闭。详见
`docs/regression-policy-scheduler-v0.1.md`。
Verification 漂移处置 v0.1 提供 `OPEN → ACKNOWLEDGED → ACCEPTED/DISMISSED` 治理流程；接受漂移会创建
带前驱和决策血缘的不可变后继基线，不覆盖旧证据，也不自动切换 RegressionPolicy。详见
`docs/verification-drift-governance-v0.1.md`。
RegressionPolicy 受控基线换版 v0.1 将实际执行基线固化到不可变策略版本；只允许切换到已接受的直接后继，
并要求独立创建、发布和激活，历史版本继续保留原基线。详见
`docs/regression-policy-baseline-transition-v0.1.md`。
VerificationBaseline 血缘与影响分析 v0.1 提供完整同根演进树、漂移决策聚合，以及历史/当前/活动
RegressionPolicyVersion 引用范围；全部为不暴露证据报文的只读视图。详见
`docs/verification-baseline-lineage-impact-analysis-v0.1.md`。
Verification 漂移治理工作台 v0.1 提供跨 Workspace 的可行动待办、分页组合筛选、可配置超期判断、治理
汇总和 `checkCode + driftKind` 同类聚合；V30 使用可重建投影避免扫描证据 JSON。详见
`docs/verification-drift-workbench-v0.1.md`。
Verification 漂移治理策略资产 v0.1 提供 Workspace/全局作用域、不可变版本、超期/聚合/提醒预算/负责人/
抑制配置，以及 `Workspace > Global > Built-in Default` 的确定性解析；V31 只落地配置与解析，所有验收策略
最终保持暂停，不发送自动提醒。详见 `docs/drift-governance-policy-assets-v0.1.md`。
Verification 漂移治理只读评估 v0.1 按 Workspace 消费解析后的策略，输出策略版本、截止时间、逐项抑制证据
和提醒候选结论；它不写入执行状态，也不创建 Notification Outbox。详见
`docs/verification-drift-governance-evaluation-v0.1.md`。
Verification 漂移治理执行账本 v0.1 允许人工显式物化候选，冻结策略/评估快照、聚合键、责任人和提醒预算；
V32 提供报告级幂等和审计，但没有定时执行器，也不创建 Notification Outbox。详见
`docs/verification-drift-governance-execution-ledger-v0.1.md`。
Verification 漂移治理提醒批次 v0.1 提供 DRAFT 预览、人工批准和显式提交；V33 在同一事务中创建
Notification Outbox、推进提醒预算并记录完整操作证据，仍不启用自动扫描。详见
`docs/verification-drift-governance-reminder-batch-v0.1.md`。
提醒批次运营闭环 v0.2 提供带原因的取消、原子替代和双向血缘；V34 增加默认关闭且只能创建 DRAFT 的到期账本
预览生成器，自动化不能批准、提交或消耗预算。详见
`docs/verification-drift-governance-reminder-operations-v0.2.md`。
治理工作台运营闭环 v0.4 提供 Workspace 内负责人分派、批量确认、批量接受/忽略、Dry Run、幂等回放和
完整命令审计；正式命令采用全有或全无，不模拟尚未接入的 RBAC。详见
`docs/verification-drift-workbench-operations-v0.4.md`。
治理工作台运营度量与 SLA v0.5 提供 Workspace 级待办 SLA、负责人负载、处置效率、批量命令质量和最多
90 日的补零趋势；查询复用事实账本、保持只读且不新增物化指标表。详见
`docs/verification-drift-workbench-operations-metrics-v0.5.md`。
策略绑定 SLA v0.6 让运营指标默认消费 Workspace/Global/内置默认三级治理策略，返回不可变策略版本和精确
秒级阈值证据；可选 `slaHours` 仅作为自描述的只读分析覆盖。详见
`docs/verification-drift-workbench-policy-bound-sla-v0.6.md`。
策略切换影响分析 v0.7 在发布/激活前比较当前有效策略与 DRAFT/PUBLISHED 候选版本，计算超期、完全抑制和
提醒候选的集合变化，并分页返回安全的变化报告。详见
`docs/verification-drift-policy-impact-analysis-v0.7.md`。
策略影响快照与发布门禁 v0.8 将分析结果固化为有时效、带 checksum 的不可变评审证据；Workspace 策略发布
和激活必须校验候选身份、当前有效基线并分别一次性消费快照。详见
`docs/verification-drift-policy-impact-snapshot-gate-v0.8.md`。
Global 聚合影响快照与覆盖门禁 v0.9 自动识别未被 ACTIVE Workspace 专属策略遮蔽的真实 Workspace，冻结
覆盖 checksum 和逐 Workspace 子证据；Global 发布与激活现在同样强制要求完整、未漂移的聚合快照。详见
`docs/verification-global-drift-policy-impact-snapshot-gate-v0.9.md`。
Global 策略影响异步分片任务 v1.0 提供持久化进度、SKIP LOCKED 租约领取、逐项事务、失败续算和显式最终封板；
封板产物直接复用 v0.9 门禁，默认不启用后台自动执行。详见
`docs/verification-global-drift-policy-impact-job-v1.0.md`。
只有 APPROVED 工作区可以生成不可变 Bundle。Bundle 首先进入 READY，显式发布后进入
PUBLISHED，制品下载接口只暴露 PUBLISHED Manifest。v0.1 以 `db://tpip-bundle/...` 标识
数据库制品仓库，签名元数据明确标记为 UNSIGNED，后续可替换为对象存储和正式签名服务。

Bundle Manifest 以规范化 JSON 原文持久化，并由 `JSON_VALID` 约束保证合法性。不能使用 MySQL
`JSON` 列保存不可变制品原文，因为数据库会重排对象字段，导致下载字节与
`artifact_checksum` 不一致。下载接口通过 `ETag` 与 `X-TPIP-Artifact-Checksum` 返回制品字节
SHA-256；Manifest 内部 `checksum` 则表示与编译时间无关的内容身份。

## 应用入口

```text
tpip-control-plane-app  配置、验证、发布控制面
tpip-runtime-app        标准调用与回调运行面
tpip-worker-app         异步任务执行器
```

Runtime 调用入口会先安全解析并缓存已激活 Bundle，然后执行 Bundle 中冻结的契约、Mapping、Policy
和 Endpoint。成功与业务执行失败均返回标准 `InvocationResponse`；Bundle 或动态路由不可用时返回
HTTP 503 和 `TPIP_RUNTIME_BUNDLE_UNAVAILABLE`。控制面已经接入 MySQL 和 Redis，但运行面仍严格
不读取配置数据库。执行流水线的阶段、错误码和当前能力边界见 `docs/runtime-pipeline-v0.1.md`。

## 下一步

1. 将渠道定义和路由规则升级为控制面不可变版本资产，并实现企业微信/钉钉专有 Provider。
2. 将静态工作负载 Token 升级为 OIDC/SPIFFE 短期身份和细粒度 RBAC。
3. 接入 Vault/云 Secret Manager，并实现 OAuth2、HMAC 等受控 Policy Provider。
4. 增加连接池、并发隔离、熔断和受幂等约束的重试预算。

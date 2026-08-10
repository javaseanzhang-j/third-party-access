# TPIP UI 治理门禁报告资产 v0.20

## 1. 目标

v0.20 将 v0.19 的一次性浏览器验收结果升级为可归档、可校验、可被流水线消费的证据资产。业务场景仍通过真实 UI
执行；Playwright 输出已验证的领域事实，生命周期编排器在停止服务、清理数据并完成无残留复核后统一生成报告。

## 2. 证据分层

### 2.1 业务证据 `business-evidence.json`

- `schemaVersion` 与 `evidenceType`：稳定识别证据契约；
- `runId / workspaceId`：关联本次隔离运行；
- `assets`：原批次、替代批次和 Outbox 主键；
- `auditChains`：两条完整不可变审计事件链；
- `auditChainChecksum`：对固定字段顺序的两条事件链计算 SHA-256；
- `assertions`：事件 UUID、Row Version、替代血缘、治理原因和 Outbox 投影断言结果。

业务证据只在所有浏览器与 API 断言均成功后写入，避免将半完成场景误标为已验证资产。

### 2.2 运行报告 `gate-report.json`

报告契约版本为 `1.0`，包含：

- Run ID、`PASSED / FAILED`、开始/结束时间和耗时；
- Workspace、浏览器 Channel 和固定隔离端口；
- 完整业务证据；
- `cleanupStatus` 与 `verifyCleanStatus`；
- Control Plane 日志、Playwright Trace、业务证据及报告路径；
- 失败阶段和退出码。失败阶段包括配置、端口预检、构建、夹具创建、服务启动、Playwright、清理和清理复核。

`gate-report.md` 是同一事实的人工可读摘要，用于发布封板、评审或问题定位；JSON 是自动化系统的唯一结构化输入。

## 3. checksum 边界

checksum 覆盖两条审计链的批次 ID、当前 Row Version，以及每个事件的 UUID、类型、Row Version、治理原因、
替代双向关联和 Outbox ID。它不覆盖运行路径、时间、数据库密码或日志，因此归档位置变化不会影响业务证据校验。

当前 checksum 用于证明报告中的事件链在生成后未变化，不替代数字签名。未来若跨信任域交换证据，应在 JSON 外层增加
组织证书签名、签名时间与密钥版本，而不是改变现有 checksum 含义。

## 4. 执行与归档

临时验收：

```bash
TPIP_MYSQL_PASSWORD='<local-password>' e2e/ui-governance-workbench/verify.sh
```

稳定归档：

```bash
TPIP_E2E_ARTIFACT_DIR=/absolute/artifact/root \
  TPIP_MYSQL_PASSWORD='<local-password>' e2e/ui-governance-workbench/verify.sh
```

稳定归档根下会创建 `tpip-ui-governance-<UTC>-<PID>` 唯一目录，不删除或覆盖旧运行。终端固定输出：

- `gate.status`；
- `gate.workspaceId`（已创建夹具时）；
- `gate.report.json`；
- `gate.report.markdown`；
- `gate.artifacts`。

本地默认使用系统 Chrome；具备 Playwright 浏览器的 CI 可设置 `TPIP_E2E_BROWSER_CHANNEL=bundled`。已构建最新 Jar
时可设置 `TPIP_E2E_SKIP_BUILD=true`。

## 5. 失败语义

业务断言失败时不会产生完整业务证据，但仍会保留 Playwright Trace、Control Plane 日志以及失败报告。退出钩子不依赖
Playwright 成功：它先终止自身启动的服务，再按 manifest 精确清理隔离 Workspace，执行独立 `verify-clean`，最后才生成
报告。清理或复核失败会将整次门禁置为 `FAILED`，不得以业务测试已通过掩盖环境残留。

报告不保存数据库密码、Token 或第三方凭据。归档系统应对目录设置适当的保留周期和访问控制。

## 6. 验收标准

- 成功运行产生业务证据、JSON 报告和 Markdown 报告；
- JSON 中 checksum 为 64 位 SHA-256 十六进制值；
- 报告资产 ID 与真实 UI/API 场景一致；
- 失败运行记录准确阶段、退出码和 Trace 路径；
- 成功和失败运行均显示 `CLEANED / VERIFIED`；
- 退出后 18082、18100 无监听，隔离 Workspace 无数据残留。

## 7. 本地验收基线

2026-08-09 已验证两条独立路径：

- Workspace `33` 使用未安装的 Playwright bundled Chromium 触发预期失败，报告标记 `PLAYWRIGHT / FAILED`，
  Trace 存在，且夹具显示 `CLEANED / VERIFIED`；
- Workspace `34` 使用系统 Chrome 完整通过，报告记录原批次 `39`、替代批次 `40`、Outbox `27`，五项业务断言
  全部通过；审计链 checksum 独立重算一致；
- UI 单元测试 `23` 个文件、`42` 项测试通过，TypeScript 与生产构建通过；
- 两次退出后 18082、18100 均无监听。

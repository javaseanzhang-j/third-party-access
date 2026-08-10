# UI Governance Workbench Acceptance Fixture

v0.16 起使用的浏览器验收隔离数据夹具。它只创建一个带 `ui-v016-*` 标识的 Workspace 及其关联资产，生成的 `manifest.properties` 保存隔离根及预置资产主键。清理前会校验 Workspace 编码、创建人和主键，再在该 Workspace 边界内枚举浏览器命令动态生成的批次、Outbox 与审计证据；不会扫描或删除其他 Workspace 的业务数据。

## 使用

```bash
export TPIP_MYSQL_PASSWORD='<local-password>'
e2e/ui-governance-workbench/fixture.sh seed /tmp/tpip-ui-v016/fixture.properties

# 无论浏览器验收成功或失败，都执行：
e2e/ui-governance-workbench/fixture.sh cleanup /tmp/tpip-ui-v016/fixture.properties
```

v0.19 起推荐直接执行完整自动化门禁，v0.20 起门禁同时生产可归档报告资产：

```bash
TPIP_MYSQL_PASSWORD='<local-password>' e2e/ui-governance-workbench/verify.sh
```

`verify.sh` 默认先构建最新 Control Plane，确认 18082/18100 未被占用，创建隔离 Workspace，启动服务并执行
`reminder-batch-audit-gate.spec.ts`。无论成功、断言失败还是进程中断，退出钩子都会停止服务并执行
`cleanup + verify-clean`。本地默认复用系统 Chrome；CI 使用 Playwright 自带浏览器时设置
`TPIP_E2E_BROWSER_CHANNEL=bundled`。已明确构建过最新 Jar 时可设置 `TPIP_E2E_SKIP_BUILD=true`。

运行日志、Playwright 失败证据、业务证据、`gate-report.json`、`gate-report.md` 和 manifest 位于输出的
`gate.artifacts` 目录；manifest 和报告均不含数据库密码。默认使用系统临时目录；需要稳定归档时设置：

```bash
TPIP_E2E_ARTIFACT_DIR=/absolute/artifact/root \
  TPIP_MYSQL_PASSWORD='<local-password>' e2e/ui-governance-workbench/verify.sh
```

脚本会在根目录下创建唯一 Run ID 子目录，不覆盖历史报告。成功报告包含资产主键、完整审计事件链、断言结果和
`SHA-256` checksum；失败报告包含失败阶段、退出码、Trace 目录及独立清理结论。报告契约见
[`../../docs/tpip-ui-governance-gate-report-v0.20.md`](../../docs/tpip-ui-governance-gate-report-v0.20.md)。

默认连接 `jdbc:mysql://127.0.0.1:3306/tpip_platform`，用户名为 `root`。可通过 `TPIP_MYSQL_URL`、`TPIP_MYSQL_USER`、`TPIP_MYSQL_DRIVER` 和 `TPIP_JAVA_HOME` 覆盖；脚本不保存数据库密码。

## 验收状态

- 一个超期且未抑制的 `EVIDENCE_CHANGED / UI_V016_FIXTURE` 漂移报告；
- 一个尚未物化的 `RESULT_CHANGED / UI_V017_COMMAND` 漂移报告，用于验证受控账本物化；
- 一个不可变 Workspace 策略版本和一条 READY 执行账本；
- 一条 CANCELLED → DISPATCHED 的批次替换链路；
- 一个仍占用下一提醒序号的 DRAFT 批次；
- 一条 ROUTED / DELIVERED Outbox 及模拟渠道投递。

`cleanup` 会在同一事务中逆向移除预置资产和 `local-ui` 在隔离 Workspace 中动态生成的命令资产，并逐个根主键确认没有残留。清理后还可用 `verify-clean` 再次执行只读确认。

v0.18 验收还会动态生成批次命令审计事件；清理逻辑按隔离 Workspace 的批次编码和 `local-ui` 操作人精确移除，
覆盖创建、批准、取消、替代血缘和提交 Outbox 事件。

v0.19 自动门禁进一步断言审计事件类型与顺序、Row Version 连续性、事件 UUID 唯一性、替代双向血缘、治理原因、
批次 Outbox 与投递投影的一致性。

v0.20 将上述已验证事实输出为版本化业务证据，并在清理完成后生成机器可读 JSON 与便于人工封板的 Markdown 报告。

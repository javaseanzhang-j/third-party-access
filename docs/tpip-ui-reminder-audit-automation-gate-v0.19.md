# TPIP UI 提醒审计自动化门禁 v0.19

## 1. 目标

v0.19 将 v0.17 的受控命令链和 v0.18 的不可变审计时间线固化为一条可重复、可失败恢复、可证明清理完成的浏览器自动化门禁。

门禁不是简单页面冒烟测试，而是从真实 UI 执行命令，再使用只读 API 校验领域状态与审计投影的一致性。

## 2. 组成

- `UiGovernanceFixture.java`：创建独立 Workspace、策略、漂移、账本和预置批次；
- `fixture.sh`：执行 `seed / cleanup / verify-clean`；
- `verify.sh`：构建、端口预检、服务启停、Playwright 执行和 finally 清理；
- `reminder-batch-audit-gate.spec.ts`：浏览器业务步骤与完整性断言；
- `playwright.config.ts`：固定 loopback Base URL、单进程 WebServer 和可选浏览器 Channel。

## 3. 自动业务场景

1. 打开隔离 Workspace 的提醒批次页；
2. 读取预置 DRAFT，确认没有审计时 UI 不伪造事件；
3. 从 UI 取消预置批次，验证原因进入审计并释放账本占用；
4. 选择到期账本并创建新的 DRAFT；
5. 从 UI 原子替代新批次；
6. 批准替代批次；
7. 显式勾选风险确认并提交 Outbox；
8. 交叉读取批次详情、审计时间线和投递投影。

## 4. 完整性断言

### 4.1 原批次

- 必须依次出现 `CREATED → CANCELLED → REPLACEMENT_LINKED`；
- Row Version 必须为 `0 → 1 → 2`；
- 三个事件 UUID 必须唯一；
- 取消和血缘事件保留替代治理原因；
- `replacedByBatchId` 必须指向新批次。

### 4.2 替代批次

- 必须依次出现 `CREATED → APPROVED → DISPATCHED`；
- Row Version 必须为 `0 → 1 → 2`；
- `replacesBatchId` 必须指向原批次；
- 提交事件、批次当前状态和投递投影的 Outbox ID 必须相同；
- 投递未运行时必须保持 `PENDING · UNROUTED`，不能误报送达。

## 5. 失败恢复

`verify.sh` 使用退出钩子统一执行：

1. 停止自己启动的 Control Plane；
2. Playwright 负责停止自己启动的 Vite；
3. 删除隔离 Workspace 下动态批次、成员、Outbox、投递和 `local-ui` 审计；
4. 执行独立 `verify-clean`；
5. 保留不含密码的临时日志和失败 Trace 路径。

门禁明确拒绝复用占用 18082 或 18100 的未知进程，避免误测其他本地实例。数据库密码仅通过环境变量传递，不写入 manifest、日志或代码。

## 6. 浏览器策略

本地默认 `TPIP_E2E_BROWSER_CHANNEL=chrome`，复用系统 Chrome，避免额外下载。CI 若已经执行 Playwright 浏览器安装，可设置：

```bash
TPIP_E2E_BROWSER_CHANNEL=bundled
```

门禁保持 `workers=1`，避免共享隔离 Workspace 上的命令竞争。Playwright Trace 仅在失败时保留。

## 7. 执行方式

```bash
TPIP_MYSQL_PASSWORD='<local-password>' e2e/ui-governance-workbench/verify.sh
```

默认会执行 Control Plane Maven Package。已有最新 Jar 时可使用 `TPIP_E2E_SKIP_BUILD=true` 缩短本地复验时间。

## 8. 验收结果

2026-08-09 首次完整通过使用隔离 Workspace `31`；加入自动 Maven Package 后的最终自包含复验使用 Workspace `32`：

- Playwright 场景 1 项通过，最终浏览器业务执行约 5.8 秒；
- 最终自动门禁测试阶段约 7.9 秒；
- 输出 `gate.status=passed`；
- `fixture.cleanup=complete`；
- `fixture.verifyClean=complete`；
- 18082、18100 无残留监听。

门禁开发过程中还验证了两种失败恢复：Playwright Chromium 缺失，以及健康按钮选择器不匹配。两次失败均完整清理 Workspace `29`、`30`，证明清理钩子不依赖测试成功。

## 9. 后续建议

v0.20 建议增加门禁报告资产：生成机器可读 JSON 与 Markdown 摘要，记录运行 ID、Workspace、事件链 checksum、测试耗时、清理结论和失败 Trace 路径，作为未来 CI/CD、发布封板和 PCS/EA 证据关联的统一输入。

# Global 影响任务封板 E2E

该场景创建隔离的 DRAFT Global 策略和版本，冻结当前 Workspace 覆盖，计算所有子快照，完成
`PENDING → READY → SEALED`，最后验证 v1.7 稳定快照 View API 与封板审计事件。

## 前置条件

- MySQL、Redis 与 Control Plane 已启动；
- Control Plane 默认地址为 `http://127.0.0.1:18082`；
- 本机安装 `curl` 与 `jq`；
- 仅在本地验收 Schema 执行。脚本会创建带 `runId` 的持久化审计资产，不执行发布或激活。

## 执行

```bash
./e2e/global-impact-seal/verify.sh
```

可覆盖：`TPIP_E2E_CONTROL_BASE`、`TPIP_E2E_OPERATOR`、`TPIP_E2E_WORKER_ID`、`TPIP_E2E_RUN_ID`。
证据写入 `e2e/global-impact-seal/evidence/{runId}`。

完成后可将 `acceptance-summary.json` 中的 ID 注入 UI Playwright 场景：

```bash
TPIP_E2E_GLOBAL_IMPACT_JOB_ID={jobId} \
TPIP_E2E_GLOBAL_IMPACT_SNAPSHOT_ID={snapshotId} \
npm --prefix tpip-ui run test:e2e
```

未提供 ID 时，封板资产跳转场景会被显式跳过，不会误用其他环境的历史数据。

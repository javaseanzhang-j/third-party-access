# Customer Lookup 本地 E2E

该目录实现 `docs/local-end-to-end-acceptance-plan.md` 中的确定性第三方接入验收场景。

## 组件

- `mock-provider.py`：监听 `127.0.0.1:19090` 的可故障注入 Mock Provider。
- `bootstrap.sh`：通过 Control Plane API 创建资产、Fixture 测试、审批并发布 Bundle。
- `finish-bootstrap.sh`：首次执行被配置校验中断时的断点续跑工具。
- `deploy-and-verify.sh`：预热、激活并执行核心调用和故障场景。
- `verify-rollback.sh`：创建第二 Bundle，验证 Canary 与回滚。
- `evidence/{runId}`：每次运行的请求、响应、指标和验收报告。

## 前置条件

- MySQL `tpip_platform` 和 Redis 已启动；
- Control Plane 监听 `18080`；
- Runtime 监听 `18081`；
- `jq`、`curl` 和 Python 3 可用；
- Runtime 使用环境变量 `TPIP_SECRET_E2E_PROVIDER_API_KEY=local-e2e-key` 启动。
- 执行包含 REMOTE_CALL 的 Bootstrap 时，Control Plane 需要显式设置：

```text
TPIP_WORKSPACE_REMOTE_CALL_ENABLED=true
TPIP_WORKSPACE_REMOTE_CALL_ALLOWED_HOSTS=127.0.0.1
TPIP_WORKSPACE_REMOTE_CALL_ALLOWED_PORTS=19090
TPIP_SECRET_E2E_PROVIDER_API_KEY=local-e2e-key
```

REMOTE_CALL 默认关闭；不应在未配置环境、Host 和 Port 白名单的进程中开启。

## 基本执行顺序

```bash
python3 e2e/customer-lookup/mock-provider.py
./e2e/customer-lookup/bootstrap.sh
./e2e/customer-lookup/deploy-and-verify.sh e2e/customer-lookup/evidence/{runId}
./e2e/customer-lookup/verify-rollback.sh e2e/customer-lookup/evidence/{runId}
```

`bootstrap.sh` 使用固定 Operation Code `e2e.customer.lookup`：存在时自动复用稳定 Operation，其他
Provider、Contract、Binding、FixtureSuite、Workspace 和 Bundle 使用 `runId` 创建新的隔离资产，
因此可以在同一 Schema 重复执行。对于同一个 `EVIDENCE_DIR` 的中断任务，使用
`finish-bootstrap.sh` 断点续跑。

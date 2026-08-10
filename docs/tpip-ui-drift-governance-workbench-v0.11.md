# TPIP UI 漂移治理工作台 v0.11

## 1. 阶段目标

把已经形成的漂移事实和治理命令接入本地 UI，使 Workspace 资产追溯能够继续进入负责人分派、确认和处置闭环。
本阶段复用既有服务端 View 与命令契约，不增加数据库迁移，也不改变漂移领域状态机。

## 2. 页面与查询能力

新增 `/drift-workbench`，并支持使用 `workspaceId` 查询参数限定 Workspace。页面提供：

- Workspace、处置范围、漂移类型、检查项编码、负责人和是否超期的组合筛选；
- 全部报告、待处置、已超期和变更项四项服务端摘要；
- 报告状态、漂移签名、负责人、年龄、到期时间和 `rowVersion` 展示；
- 报告到 Workspace 资产详情的反向追溯；
- 服务端分页和显式刷新，不从当前页推断全局摘要。

查询使用既有接口：

```text
GET /control/v1/verification-drift-workbench/reports
GET /control/v1/verification-drift-workbench/summary
```

## 3. 受控操作流程

批量操作只允许选择同一 Workspace 的报告，并复用以下命令：

```text
POST /control/v1/verification-drift-workbench/governance-reviews:assign
POST /control/v1/verification-drift-workbench/governance-reviews:acknowledge
POST /control/v1/verification-drift-workbench/governance-reviews:dispose
```

前端执行顺序固定为：

1. 用户选择报告并填写负责人（分派时）和操作理由；
2. 前端携带每条报告的 `reportId + rowVersion` 发起 `dryRun=true`；
3. 只有全部目标均可执行且无拒绝项时，才开放“确认实际执行”；
4. 实际执行重新生成 `Idempotency-Key`，不复用 Dry Run 的命令键；
5. 成功后刷新报告和摘要；冲突由用户刷新事实后重新决策，不自动重放命令。

所有命令携带 `X-Operator: local-operator`。这是本地单用户阶段的审计占位值，不代表认证身份；OIDC/JWT 与
RBAC 仍遵循按需恢复策略。

## 4. 安全与一致性边界

- 页面不修改或隐藏原始漂移事实；接受操作由服务端建立后继基线；
- `rowVersion` 是乐观并发前置条件，不能以页面缓存覆盖服务端事实；
- Dry Run 只是命令预检，实际执行仍必须再次通过服务端校验；
- 批量操作保持服务端定义的全有或全无语义；
- 前端不保存数据库凭据、Redis 信息或第三方密钥；
- 当前 UI 仅供本机单用户使用，不应暴露到不可信网络。

## 5. 验收结果

```text
Test Files  17 passed (17)
Tests       31 passed (31)
vue-tsc / tsc / vite build passed
漂移工作台 chunk 10.55 kB（gzip 4.19 kB）
最大 JS chunk 427.27 kB（gzip 145.71 kB）
Playwright 静态发现 7 个场景
```

本阶段未启动浏览器。服务端契约未发生变化，命令适配由单元测试覆盖。真实环境只读验收结果为：Workspace 23
待处置列表 0 条，汇总包含 1 条已接受报告和 1 个变更项；未执行分派、确认、接受或驳回，避免为 UI 验收制造
业务数据。验收后 Control Plane 已优雅停止，18082 端口已释放。

## 6. 下一阶段建议

UI v0.12 建设治理操作证据与审计回查：支持按 `commandKey` 查看 Dry Run/实际执行结果、逐项拒绝原因、幂等重放
标识和最终版本，并从 Workspace 详情增加直接进入已限定范围漂移工作台的入口。

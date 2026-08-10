# RegressionPolicy 定时回归与漂移告警 v0.1

## 1. 目标与边界

本阶段把手动回归升级为受治理的自动化能力：使用版本化 `RegressionPolicy` 绑定一个不可变
`VerificationBaseline`，按固定间隔执行回归，持久化调度状态，并在漂移或连续失败时进入现有 Notification
Outbox。

自动调度全局默认关闭，单个策略也必须经过“创建、创建版本、发布、激活”四个显式步骤。两个门禁同时
开启后才会执行。v0.1 使用 UTC `Instant + interval`，暂不引入 Cron、时区、维护窗口或节假日日历。

## 2. 资产与状态模型

```text
RegressionPolicy (稳定身份，DRAFT / PAUSED / ACTIVE)
  +-- RegressionPolicyVersion (不可变参数，DRAFT / PUBLISHED)
  |     baselineId / interval / failureBackoff / maximumConsecutiveFailures
  +-- RegressionScheduleState (运行态)
        nextRunAt / lease / consecutiveFailures / last outcome
```

- 发布版本后，策略进入 `PAUSED`，不会自动运行；已发布版本不可修改。
- 激活必须提供首次执行时间，并以 `rowVersion` 做乐观并发控制。
- 更新策略参数必须先暂停，再创建和发布新版本；发布时若旧执行仍持有有效租约则拒绝。
- 人工暂停不会中断已发出的远程请求。正在执行的任务可以完成并记录结果，但不会再次排期。
- 达到连续失败阈值后系统自动暂停策略，并增加策略 `rowVersion`。
- 策略上的 `baselineId` 表示初始基线；Scheduler 始终使用当前不可变版本的 `baselineId`。

## 3. 调度和多实例语义

每个实例周期性查询到期任务，但真正执行前必须通过数据库条件更新获得租约。租约同时校验策略为
`ACTIVE`、当前版本一致、`nextRunAt` 未变化且旧租约已过期。因此多实例可以共同轮询，而同一到期批次只有
一个实例取得执行权。

成功后按完成时间加 `interval` 排下一次执行，连续失败清零。失败后按完成时间加 `failureBackoff` 重试；达到
阈值则 `nextRunAt=NULL` 并暂停。租约用于进程异常后的恢复，不提供分布式强制取消；超时必须大于单次验证的
最大合理时长。

## 4. 漂移与通知

- `NO_DRIFT`：仅更新调度状态和漂移报告，不产生通知，避免健康噪声。
- `DRIFTED`：写入 `TPIP_VERIFICATION_DRIFT_DETECTED` Outbox 事件。
- 连续失败自动暂停：写入 `TPIP_REGRESSION_POLICY_SUSPENDED` Outbox 事件。

事件载荷只包含策略、版本、基线、运行、报告、数量和受控原因码，不包含 Fixture 输入、Provider 响应、URL、
Header、Secret 或异常消息。实际渠道仍由现有通知路由、模板和投递治理决定。

## 5. 安全门禁

调度器不会绕过 Verification Engine。若基线包含 `REMOTE_CALL` Fixture，仍需同时满足远程调用开关、环境与
Host/Port 白名单、HTTPS/本地例外、`IDEMPOTENT`、调用次数、报文大小和超时限制。生产启用自动回归前必须
确认目标接口可安全重放。

配置示例：

```yaml
tpip:
  regression-scheduler:
    enabled: false
    poll-interval: 1m
    batch-size: 10
    lease: 10m
```

## 6. API 配置流程

```text
POST /control/v1/regression-policies
POST /control/v1/regression-policies/{policyId}/versions
POST /control/v1/regression-policies/{policyId}/versions/{versionId}:publish
POST /control/v1/regression-policies/{policyId}:activate
POST /control/v1/regression-policies/{policyId}:pause
GET  /control/v1/regression-policies/{policyId}/state
GET  /control/v1/regression-policies/{policyId}/versions
```

版本约束：执行间隔 60 秒至 30 天；失败退避 60 秒至 1 天；连续失败阈值 1 至 100。创建版本时
`baselineId` 可选，缺省继承当前版本；显式换版只接受已接受漂移产生的直接后继，详见
`docs/regression-policy-baseline-transition-v0.1.md`。所有变更命令要求 `X-Operator`，策略生命周期变更写入
审计事件。

## 7. 数据库

V27 新增：

- `tpip_regression_policy`：策略身份、当前版本、状态和乐观锁；
- `tpip_regression_policy_version`：不可变调度参数；
- `tpip_regression_schedule_state`：排期、租约、连续失败和最近一次运行结果。

外键把策略绑定到 VerificationBaseline，把最近运行和漂移报告绑定到原始证据。调度状态是可恢复运行态，
不是配置资产版本的一部分。

V29 为 `tpip_regression_policy_version` 增加非空 `baseline_id`，并从策略初始基线回填历史版本。此后版本
独立保存实际执行基线，数据库外键保证引用存在。

## 8. 后续演进

1. 维护窗口、时区日历、随机抖动和环境级并发配额；
2. 漂移确认、基线换版的职责分离与双人审批；
3. Worker 化、任务取消、租约续期和超长任务隔离；
4. 策略运行趋势、SLO 和告警聚合；
5. 与未来 OIDC/RBAC 对接策略发布与激活权限。

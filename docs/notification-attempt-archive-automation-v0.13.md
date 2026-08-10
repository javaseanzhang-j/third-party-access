# 通知投递尝试归档自动化与可恢复性抽检基线 v0.13

## 1. 阶段目标

v0.13 在 v0.12 可信归档与安全清理基线上增加自动归档编排、跨实例租约、不可变存储语义、验证历史、周期性回读抽检和 Prometheus 指标。

自动化只允许执行以下动作：

- 扫描已经结束并越过安全延迟的标准时间窗；
- 创建新归档，或恢复同一时间窗中处于 `STORED`、`FAILED` 的批次；
- 对新制品执行首次回读验证；
- 对 `VERIFIED`、`PURGED` 制品执行周期性完整性抽检。

自动化不得调用在线证据清理接口。`purge-enabled` 仍默认关闭，清理继续作为独立受控操作。

## 2. 时间窗扫描

调度器按 UTC Epoch 对齐固定窗口，默认参数为：

- 归档窗口：1 天；
- 完成安全延迟：1 天；
- 回看范围：365 天；
- 每轮最多处理 3 个发生状态变化的批次；
- 轮询间隔：1 小时。

扫描从最早窗口开始，保证积压按时间顺序逐步收敛。无证据窗口和已经完成的窗口不会占用单轮处理额度。

归档时间窗禁止与已有批次重叠。自动任务遇到精确匹配的已有批次时：

- `STORED`：继续执行首次验证；
- `FAILED`：使用原时间窗和原 ID 水位重试，成功后验证；
- `VERIFIED`、`PURGED`：跳过；
- 非精确重叠：跳过并保留已有事实。

## 3. 跨实例执行租约

V21 新增数据库租约表。每轮任务必须先获取固定名称租约，租约包含实例所有者和过期时间：

- 获取成功的实例负责本轮归档和抽检；
- 未获得租约的实例记录 `lease.missed` 指标并退出；
- 正常完成后按所有者释放；
- 实例异常退出后，其他实例可以在租约超时后接管。

数据库唯一时间窗、不可变制品写入和租约共同提供多节点环境下的防重保护。租约不是清理授权，也不能绕过任何归档校验门禁。

## 4. 写一次存储语义

本地文件适配器不再允许覆盖同一 `batchCode` 对应的制品：

- 目标不存在时采用临时文件加原子移动；
- 目标存在且字节完全一致时视为幂等重试；
- 目标存在但内容不同则拒绝写入。

该行为定义了对象存储适配器必须遵循的契约。生产对象存储应进一步启用 Bucket Versioning、WORM/Object Lock、服务端加密、独立访问审计和保留策略。

## 5. 验证历史与抽检

V21 新增 `tpip_notification_attempt_archive_verification`，每次验证独立记录：

- 验证类型：`INITIAL`、`MANUAL`、`DRILL`；
- 结果：`PASSED`、`FAILED`；
- 当时的制品 SHA-256；
- 操作人、时间和耗时；
- 失败原因。

首次验证失败不会被吞掉，失败记录持久化后继续向调用方返回错误。周期性抽检默认间隔 30 天，每轮最多抽检 10 个超过周期且近期没有成功验证记录的批次。

抽检执行完整回读流程，包括制品字节数、SHA-256、NDJSON 解析、记录数、环境、时间窗、ID 水位、清单摘要和清单语义绑定。`PURGED` 批次仍保留归档制品，因此也必须参加抽检。

## 6. 接口

在 v0.12 接口基础上新增：

```text
POST /control/v1/notification-attempt-archives/{id}:audit
GET  /control/v1/notification-attempt-archives/{id}/verifications?limit={limit}
```

手工抽检要求 `X-Operator`，验证类型固定为 `MANUAL`。验证历史为只读证据，不提供修改和删除接口。

## 7. 配置

```text
TPIP_NOTIFICATION_ATTEMPT_ARCHIVE_AUTOMATION_ENABLED=false
TPIP_NOTIFICATION_ATTEMPT_ARCHIVE_POLL_INTERVAL=1h
TPIP_NOTIFICATION_ATTEMPT_ARCHIVE_ENVIRONMENTS=default
TPIP_NOTIFICATION_ATTEMPT_ARCHIVE_WINDOW=1d
TPIP_NOTIFICATION_ATTEMPT_ARCHIVE_COMPLETION_DELAY=1d
TPIP_NOTIFICATION_ATTEMPT_ARCHIVE_LOOKBACK=365d
TPIP_NOTIFICATION_ATTEMPT_ARCHIVE_MAXIMUM_BATCHES_PER_CYCLE=3
TPIP_NOTIFICATION_ATTEMPT_ARCHIVE_AUTOMATION_LEASE=30m
TPIP_NOTIFICATION_ATTEMPT_ARCHIVE_VERIFICATION_DRILL_ENABLED=true
TPIP_NOTIFICATION_ATTEMPT_ARCHIVE_VERIFICATION_DRILL_INTERVAL=30d
TPIP_NOTIFICATION_ATTEMPT_ARCHIVE_VERIFICATION_MAXIMUM_BATCHES_PER_CYCLE=10
```

自动化总开关默认关闭。抽检开关只有在自动化总开关开启时生效。首次启用前应确认归档目录容量、服务账号权限、窗口大小和历史积压规模。

## 8. 指标与告警建议

```text
tpip_notification_attempt_archive_batches{status}
tpip_notification_attempt_archive_automation_total{outcome}
tpip_notification_attempt_archive_verifications_total{type,result}
tpip_notification_attempt_archive_verification_failures{window="24h"}
tpip_notification_attempt_archive_lease_missed_total
```

建议至少配置：

- `FAILED` 批次数量大于 0；
- 24 小时验证失败数大于 0；
- 连续多个周期没有新的 `VERIFIED` 批次但在线证据持续增长；
- 归档介质容量或对象存储写入错误；
- 租约长期无法获取或长时间未释放。

## 9. 生产启用顺序

1. 部署 V21，保持自动化和清理关闭；
2. 接入生产对象存储适配器并完成写一次、回读和故障注入测试；
3. 在非生产环境开启自动归档，观察容量、耗时和失败指标；
4. 生产环境先用较小的每轮批次数消化积压；
5. 完成至少一次人工抽检和恢复演练；
6. 独立评审在线清理审批流程，不因自动归档稳定而自动开启清理。

下一阶段应建设 S3/OSS 兼容对象存储适配器、OIDC/RBAC、双人清理审批和归档容量/积压 SLO。

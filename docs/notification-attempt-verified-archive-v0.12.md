# 通知投递尝试可信归档与安全清理基线 v0.12

## 1. 目标与边界

本阶段把在线表中的通知投递尝试证据转换为可独立校验的归档制品，并为后续受控清理建立安全门禁。它解决的是在线证据持续增长、长期审计留存和误删防护问题，不承担日志检索、报表分析或生产级对象存储能力。

清理能力默认关闭。归档完成不等于允许清理，只有经过独立回读校验、已越过在线保留期且未被法律保全的批次才具备清理资格。

## 2. 状态机

```text
CREATED ──写入制品──> STORED ──回读校验──> VERIFIED ──受控清理──> PURGED
   │                     │
   └──────失败───────────┴──────────────> FAILED ──按固定水位重试──> STORED
```

- `CREATED`：批次身份、时间窗和记录 ID 水位已冻结。
- `STORED`：NDJSON 制品与清单已持久化，但尚不能作为清理依据。
- `VERIFIED`：制品经过存储介质回读和完整性、语义一致性校验。
- `PURGED`：该批次对应的在线证据已按精确边界清理，并记录实际清理数量。
- `FAILED`：制品构建或存储失败，只允许使用原时间窗和原 ID 水位重试。

已经进入 `VERIFIED` 或 `PURGED` 的批次不可重写，归档批次以 `batchCode` 作为不可变身份。

## 3. 批次冻结规则

- 归档范围使用左闭右开时间窗 `[windowStart, windowEnd)`，结束时间不得晚于当前时间。
- 单个时间窗最长 31 天；同一环境和时间窗只能存在一个批次。
- 首次读取按尝试记录 ID 升序，并冻结 `firstAttemptId`、`lastAttemptId` 和 `recordCount`。
- 默认单批最多 100,000 条、制品最多 100 MiB，超过边界必须拆分时间窗。
- 失败重试不得重新解释范围，只能读取原时间窗内且位于原 ID 水位之间的记录，并要求数量完全一致。

时间窗约束负责业务范围，ID 水位负责阻止迟到数据或范围漂移改变已经建立的归档事实。

## 4. 制品与清单

制品格式为确定性 NDJSON，格式标识为 `tpip.notification-attempt-archive/v1`。每行是一条规范化后的投递尝试证据，字段顺序和 JSON 表达由 Canonical JSON 规则固定，制品以 UTF-8 编码并以换行符分隔。

清单至少包含：

- 格式版本、批次编码和环境编码；
- 时间窗起止；
- 首尾尝试 ID 和记录数量；
- 制品 SHA-256 与字节数。

数据库同时保存制品 SHA-256 和规范化清单 SHA-256。校验必须从归档存储重新读取制品，并验证：

1. 字节数与制品 SHA-256；
2. NDJSON 行数、格式版本和环境；
3. 每条记录时间均在冻结窗口内；
4. 首尾 ID 与冻结水位一致；
5. 清单 SHA-256；
6. 清单中的身份、窗口、水位、数量和制品摘要与批次元数据完全一致。

任一校验失败都不得进入 `VERIFIED`，更不得清理在线数据。

## 5. 法律保全与安全清理

法律保全由 `legalHold`、原因、操作人和时间组成。设置保全必须填写原因；释放保全保留审计轨迹。处于保全状态的批次禁止清理。

清理同时满足以下门禁：

- `TPIP_NOTIFICATION_ATTEMPT_PURGE_ENABLED=true` 被显式开启，默认值为 `false`；
- 批次状态为 `VERIFIED` 且未被法律保全；
- `windowEnd` 早于 `当前时间 - attemptOnlineRetention`，默认在线保留期为 90 天；
- 删除前再次从存储介质回读并执行完整校验；
- 删除 SQL 同时绑定批次状态、保全状态、环境、时间窗和 ID 水位；
- 实际删除数量必须等于清单记录数，否则整个事务回滚；
- 成功后记录操作人、时间和实际删除数量。

因此，配置误开、批次选错、制品损坏、清单漂移、保全遗漏或删除范围不一致中的任一情况都会阻止清理。

## 6. 接口

```text
POST /control/v1/notification-attempt-archives
GET  /control/v1/notification-attempt-archives?environmentCode={env}&limit={limit}
GET  /control/v1/notification-attempt-archives/{id}
POST /control/v1/notification-attempt-archives/{id}:retry
POST /control/v1/notification-attempt-archives/{id}:verify
POST /control/v1/notification-attempt-archives/{id}:legal-hold
POST /control/v1/notification-attempt-archives/{id}:purge
```

所有写接口要求 `X-Operator`。当前请求头只承载审计身份，不替代生产环境的 OIDC/RBAC 和双人审批。

## 7. 配置

```text
TPIP_NOTIFICATION_ATTEMPT_ARCHIVE_DIRECTORY
TPIP_NOTIFICATION_ATTEMPT_ARCHIVE_MAXIMUM_RECORDS
TPIP_NOTIFICATION_ATTEMPT_ARCHIVE_MAXIMUM_BYTES
TPIP_NOTIFICATION_ATTEMPT_PURGE_ENABLED=false
TPIP_NOTIFICATION_ATTEMPT_ONLINE_RETENTION=90d
```

本地文件适配器用于开发与协议验证，限制读取路径只能位于配置根目录下。生产环境应替换为支持服务端加密、版本控制、WORM/Object Lock、生命周期策略和独立访问审计的对象存储适配器；领域端口和应用状态机保持不变。

## 8. 数据模型与审计

Flyway `V20__notification_attempt_verified_archive.sql` 新增 `tpip_notification_attempt_archive_batch`，保存批次冻结边界、制品定位、双重摘要、状态、法律保全、验证、清理和失败信息。

阶段性审计主体包括批次创建、制品存储、验证、法律保全变更和清理。后续生产化应增加统一不可篡改审计事件、审批单关联、制品存储请求 ID 和定期可恢复性抽检。

## 9. 下一阶段建议

v0.13 优先建设对象存储/WORM 适配器和归档调度器，再接入 OIDC/RBAC、清理双人审批、容量指标、积压告警和定期恢复演练。自动调度只能创建与验证归档批次，不应默认自动开启在线数据清理。

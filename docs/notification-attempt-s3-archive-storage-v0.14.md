# 通知投递尝试 S3 归档存储基线 v0.14

## 1. 目标

v0.14 将归档制品存储从单一本地文件实现扩展为可切换的存储适配器，并提供面向生产环境的 S3 兼容实现。领域层和归档状态机只依赖 `NotificationAttemptArchiveStore`，不感知 AWS、S3、OSS 或具体 HTTP 客户端。

支持两种模式：

- `FILESYSTEM`：默认模式，用于本地开发和协议验证；
- `S3`：生产模式，使用 AWS SDK for Java v2，可连接 AWS S3 或实现相同接口的兼容对象存储。

切换存储不会修改已发布归档批次。一个环境投入使用后不得直接切换存储并假定旧 `artifactUri` 能被新适配器读取，迁移必须通过独立制品迁移流程完成。

## 2. S3 写入协议

归档对象键固定为：

```text
{prefix}/{batchCode}.ndjson
```

写入过程执行以下门禁：

1. 调用 Bucket 能力探测；
2. 根据策略校验 Versioning 和 Object Lock；
3. 使用 `If-None-Match: *` 执行条件写入，防止覆盖同名对象；
4. 传递 SHA-256 Checksum，并写入 `tpip-sha256`、`tpip-format` 元数据；
5. Object Lock 为必需时，写入配置的 `GOVERNANCE` 或 `COMPLIANCE` 保留模式和到期时间；
6. 保存 PutObject 返回的 `versionId`；
7. 生成绑定版本的 `s3://bucket/key?versionId=...` 制品地址。

同名对象竞争时，只在远端内容完全相同时视为幂等成功；内容不同则拒绝覆盖。

## 3. 安全回读

回读必须满足：

- URI Scheme 必须为 `s3`；
- Bucket 必须与配置完全一致；
- Key 必须位于配置 Prefix 下并以 `.ndjson` 结尾；
- 有 `versionId` 时必须读取精确对象版本；
- 下载前先调用 HeadObject 检查大小上限；
- 下载后的实际字节数必须等于 HeadObject 返回值。

制品内容的 SHA-256、NDJSON、时间窗、ID 水位和清单语义仍由 v0.12 应用层验证，存储适配器不重复解释业务格式。

## 4. 能力探测

S3 探测依次执行：

- HeadBucket：连通性和最小访问权限；
- GetBucketVersioning：版本控制状态；
- GetObjectLockConfiguration：Object Lock 状态。

默认生产策略要求 Versioning 和 Object Lock 同时启用。任一必需能力缺失时：

- 存储健康状态为 `DOWN`；
- 新归档写入被拒绝；
- 已有批次状态和在线证据不受影响；
- 自动清理依然不会被调用。

对于只实现部分 S3 API 的兼容存储，可显式关闭对应门禁用于非生产验证，但生产标准不建议关闭。

## 5. 健康检查

新增 Actuator 健康组件：

```text
notificationAttemptArchiveStorage
```

新增无敏感信息的控制面查询：

```text
GET /control/v1/notification-attempt-archives/storage-health
```

返回 Provider、连通性、策略就绪状态、Versioning、Object Lock 和安全错误摘要，不返回 Access Key、Secret Key、Session Token 或完整 SDK 异常内容。

`FILESYSTEM` 模式检查目录是否可创建、可读和可写；`S3` 模式检查 Bucket 能力和生产策略。

## 6. 配置

```text
TPIP_NOTIFICATION_ATTEMPT_ARCHIVE_STORAGE_PROVIDER=FILESYSTEM|S3
TPIP_NOTIFICATION_ATTEMPT_ARCHIVE_S3_BUCKET
TPIP_NOTIFICATION_ATTEMPT_ARCHIVE_S3_PREFIX=notification-attempts/
TPIP_NOTIFICATION_ATTEMPT_ARCHIVE_S3_REGION=us-east-1
TPIP_NOTIFICATION_ATTEMPT_ARCHIVE_S3_ENDPOINT
TPIP_NOTIFICATION_ATTEMPT_ARCHIVE_S3_PATH_STYLE_ACCESS=false
TPIP_NOTIFICATION_ATTEMPT_ARCHIVE_S3_ACCESS_KEY
TPIP_NOTIFICATION_ATTEMPT_ARCHIVE_S3_SECRET_KEY
TPIP_NOTIFICATION_ATTEMPT_ARCHIVE_S3_SESSION_TOKEN
TPIP_NOTIFICATION_ATTEMPT_ARCHIVE_S3_REQUIRE_VERSIONING=true
TPIP_NOTIFICATION_ATTEMPT_ARCHIVE_S3_REQUIRE_OBJECT_LOCK=true
TPIP_NOTIFICATION_ATTEMPT_ARCHIVE_S3_RETENTION=3650d
TPIP_NOTIFICATION_ATTEMPT_ARCHIVE_S3_OBJECT_LOCK_MODE=COMPLIANCE
TPIP_NOTIFICATION_ATTEMPT_ARCHIVE_S3_CONNECTION_TIMEOUT=3s
TPIP_NOTIFICATION_ATTEMPT_ARCHIVE_S3_SOCKET_TIMEOUT=30s
```

静态凭证只允许通过运行环境注入。未配置静态凭证时使用 AWS SDK 默认凭证链，以便生产环境使用 IAM Role、Workload Identity 或容器任务角色。任何凭证都不得写入数据库、归档清单、日志或健康详情。

自建 MinIO 或其他兼容服务通常需要配置 Endpoint 和 Path Style Access，是否支持 Versioning、Object Lock、条件写入与版本读取必须通过上线前兼容性测试确认。

## 7. 权限建议

归档服务账号应使用独立 Bucket/Prefix，并遵循最小权限。至少需要：

- Bucket 连通和位置/能力查询权限；
- Versioning、Object Lock 配置只读权限；
- PutObject、GetObject、GetObjectVersion 和 HeadObject；
- 写入对象保留信息所需权限。

应用服务账号不应拥有 DeleteObject、DeleteObjectVersion、PutBucketVersioning 或修改 Bucket Object Lock 配置的权限。制品生命周期删除应由独立治理账号和存储生命周期策略管理。

## 8. 生产验收

1. 创建独立 Bucket，并在创建阶段启用 Object Lock；
2. 启用 Versioning、默认加密和访问日志；
3. 配置最小权限工作负载身份；
4. 在清理关闭状态下验证健康探测；
5. 归档一批非生产证据并确认返回 `versionId`；
6. 验证同内容幂等、不同内容不可覆盖；
7. 执行手工回读抽检和故障注入；
8. 验证业务账号无法删除对象版本或缩短保留期；
9. 再独立评审自动归档开关，始终保持在线清理独立授权。

本阶段没有数据库迁移，沿用 V20/V21 的批次和验证历史模型。

## 9. 后续阶段

下一阶段应建设 OIDC/RBAC、清理双人审批、审批凭证与归档批次绑定，以及面向生产的归档积压、容量和恢复时间 SLO。

## 10. 延期待办：本地 MinIO 实际联调

状态：`DEFERRED / ON_DEMAND`。

本地 MinIO 不作为后续安全治理阶段的前置门禁。现阶段保留已经完成的 S3 兼容适配器、配置模型、能力探测和模拟自动化测试，继续使用 `FILESYSTEM` 作为默认本地存储。

出现以下任一条件时再启动 MinIO 联调：

- 项目需要验证真实 S3 兼容对象存储；
- 准备开启自动归档；
- 进入生产对象存储选型或上线验收；
- 需要验证 Versioning、Object Lock、条件写入和版本精确回读；
- 需要执行对象存储故障注入或恢复演练。

按需恢复时完成以下待办：

- 提供本地 MinIO 容器和配置；
- 创建独立 Bucket 与最小权限用户；
- 启用 Versioning 和 Object Lock；
- 使用 `GOVERNANCE` 短保留期避免本地测试对象长期锁定；
- 验证写入、幂等、覆盖拒绝、`versionId` 回读和健康降级；
- 增加真实 MinIO 集成测试与操作说明。

该延期不影响 OIDC/RBAC、双人审批和审计治理继续实施。

参考：

- [AWS SDK for Java 2.x S3 示例](https://docs.aws.amazon.com/sdk-for-java/latest/developer-guide/java_s3_code_examples.html)
- [Amazon S3 Object Lock 管理说明](https://docs.aws.amazon.com/AmazonS3/latest/userguide/object-lock-managing.html)

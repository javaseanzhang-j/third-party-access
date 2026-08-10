# TPIP 发布封板与证据索引 v0.21

## 1. 目标

v0.21 将源码版本信息、构建结果、部署制品、数据库迁移和 v0.20 浏览器治理证据绑定为一次独立的本地发布记录。
它解决“哪些二进制和前端资源通过了哪次验收”的可追溯问题，不承担部署、制品上传或生产审批。

## 2. 封板流程

`seal-release.sh` 按固定顺序执行：

1. Java 21 全量 `mvn clean package`；
2. UI Vitest 全量测试；
3. UI TypeScript 检查与生产构建；
4. 复制 Control Plane、Runtime、Worker Jar，UI `dist` 和数据库迁移到独立发布目录；
5. 执行 v0.20 隔离 Workspace 浏览器治理门禁；
6. 读取治理报告、业务审计链和清理结论；
7. 生成 `release-evidence.json`、`release-evidence.md` 和 `artifact-checksums.sha256`；
8. 独立重算发布清单、文件、目录和审计链 checksum。

封板不启动 Runtime 或 Worker，也不发布、激活业务 Bundle，不调用外部制品库、PCS 或 EA 服务。

## 3. 发布状态

| 状态 | 含义 |
| --- | --- |
| `SEALED` | Java/UI 验证、七类必需制品、治理门禁、审计链、清理和复核全部通过 |
| `REJECTED` | 治理浏览器门禁明确失败 |
| `INCOMPLETE` | 配置、构建、测试、产物收集或治理报告没有完成 |

脚本退出码与证据状态同时保留。失败时仍尽力生成 `REJECTED/INCOMPLETE` 报告，但不会用报告生成成功覆盖原始失败。

## 4. 证据契约

JSON Schema 位于 `e2e/release-evidence/schema/release-evidence-v1.schema.json`，契约版本为 `1.0`。核心字段包括：

- `releaseId / status / signatureStatus`；
- Git Commit、dirty 状态或明确的 `sourceIdentity.status=UNAVAILABLE`；
- Platform、UI 和最高 Flyway Migration 版本；
- Java、Maven、Node 与浏览器 Channel；
- Maven 测试、UI 测试、UI 构建和治理门禁结果；
- 七类必需制品及 SHA-256；
- 治理 Run ID、Workspace、审计链 checksum 和清理状态；
- PCS、EA 与 EESIS 标准关联扩展字段；
- 发布清单自身的 `manifestChecksum`。

七类必需制品为三个应用 Jar、UI `dist`、数据库迁移目录、治理门禁报告和治理业务证据。目录 checksum 对排序后的
逐文件相对路径、长度与 SHA-256 清单再次计算 SHA-256，因此增加、删除、改名或修改任一文件都会被发现。

当前工作空间如果没有 Git 元数据，会如实记录 `UNAVAILABLE`，不伪造 Commit。此时证据仍能证明封板制品自生成后
未变化，但不能声明可从某个 Commit 重现。接入正式源码仓库后，同一契约会自动记录 Commit 和 dirty 状态。

## 5. 安全与信任边界

- 数据库密码只通过环境变量传递，不进入报告、manifest 或构建制品索引；
- 校验器拒绝证据中的目录逃逸路径；
- `manifestChecksum` 防止修改制品描述或验证结论；
- 当前 `signatureStatus=UNSIGNED`，SHA-256 不替代发布者身份和数字签名；
- v0.21 不引入 OIDC/RBAC、MinIO、制品库、证书签名或双人审批。

未来跨机器或跨信任域分发时，应增加组织签名、可信时间戳和不可变对象存储，不改变当前 checksum 的语义。

## 6. 使用

```bash
TPIP_RELEASE_EVIDENCE_DIR=/absolute/release/root \
  TPIP_MYSQL_PASSWORD='<local-password>' \
  e2e/release-evidence/seal-release.sh
```

独立复核：

```bash
node e2e/release-evidence/verify-release-evidence.mjs \
  /absolute/release/root/<release-id>/release-evidence.json
```

## 7. 本地验收基线

2026-08-09 完成首次 `SEALED` 封板：

- Release ID：`tpip-release-20260809T112939Z-21988`；
- Java 全量测试 308 项通过，UI 23 个文件、42 项测试通过，UI 生产构建通过；
- Platform `0.1.0-SNAPSHOT`、UI `0.1.0`、数据库最高迁移 `V40`；
- v0.20 门禁使用 Workspace `35`，状态 `PASSED`，数据 `CLEANED / VERIFIED`；
- 七类必需制品全部存在，发布清单独立复核通过；
- 原始发布目录中未发现本地数据库密码；
- 复制发布目录并修改 UI `index.html` 后，校验器以 `directory checksum mismatch: UI_DIST` 拒绝；
- 退出后 18082、18100 无残留监听。

本次源码身份为 `UNAVAILABLE`，签名状态为 `UNSIGNED`，两项限制均已显式写入证据，不影响其作为当前本地工程
封板基线，但不得表述为已完成生产软件供应链签名。

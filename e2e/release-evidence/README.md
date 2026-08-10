# TPIP Release Evidence

v0.21 本地发布封板入口。它执行 Java 全量测试与打包、UI 测试与构建、v0.20 治理浏览器门禁，把三个应用 Jar、
UI `dist`、数据库迁移和门禁证据复制到独立目录，生成 SHA-256 清单、JSON/Markdown 发布证据，并再次独立校验。

## 执行

```bash
TPIP_MYSQL_PASSWORD='<local-password>' e2e/release-evidence/seal-release.sh
```

默认输出到系统临时目录。需要保存历史封板资产时：

```bash
TPIP_RELEASE_EVIDENCE_DIR=/absolute/release/root \
  TPIP_MYSQL_PASSWORD='<local-password>' e2e/release-evidence/seal-release.sh
```

本地默认使用系统 Chrome；具备 Playwright bundled Chromium 的环境可以设置
`TPIP_E2E_BROWSER_CHANNEL=bundled`。脚本不保存数据库密码。

## 状态

- `SEALED`：构建、测试、全部产物、治理门禁、审计 checksum 和清理复核全部通过；
- `REJECTED`：治理门禁明确失败；
- `INCOMPLETE`：构建、测试、产物收集或门禁报告尚未完成。

当前签名状态固定为 `UNSIGNED`。checksum 证明制品和清单内容未变化，但不替代组织数字签名。

## 独立校验

```bash
node e2e/release-evidence/verify-release-evidence.mjs \
  /absolute/release/root/<release-id>/release-evidence.json
```

校验器拒绝目录逃逸，重新计算发布清单、文件、目录和治理审计链 checksum，并重新检查封板前置条件。

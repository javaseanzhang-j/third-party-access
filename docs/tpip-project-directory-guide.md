# TPIP 工程目录与文件说明

## 1. 文档目的

本文用于说明 TPIP（Third-Party Integration Platform）工程中的目录职责、模块边界、关键文件和生成物，作为开发、维护、交付和新成员了解工程的导航文档。

工程根目录：

```text
/Users/xiaofengzhang/workstation/codex_workspace/third-party-access
```

当前工程由 Java 21 Maven 多模块后端、Vue 3 前端、MySQL/Flyway 数据库迁移、架构文档和端到端验收资产组成。

## 2. 根目录结构

```text
third-party-access/
├── pom.xml
├── README.md
├── AGENTS.md
├── .gitignore
│
├── config/
├── database/
├── docs/
├── e2e/
├── var/
│
├── tpip-bom/
├── tpip-shared-kernel/
├── tpip-contract-api/
├── tpip-domain/
├── tpip-mapping-engine/
├── tpip-policy-engine/
├── tpip-bundle/
├── tpip-runtime-core/
├── tpip-adapters/
├── tpip-control-plane-app/
├── tpip-runtime-app/
├── tpip-worker-app/
├── tpip-test-kit/
│
├── tpip-ui/
└── tpip/
```

## 3. 根目录文件

| 文件 | 作用 |
| --- | --- |
| `pom.xml` | Maven 父工程，声明 13 个后端模块、Java 21、Spring Boot 和统一插件版本 |
| `README.md` | 工程总入口，记录能力、运行方式、接口、阶段成果和文档链接 |
| `AGENTS.md` | Codex 在本工程中的开发约束，如领域层隔离、Bundle 不可变、Mapping Engine 禁止访问数据库等 |
| `.gitignore` | 忽略本地配置、构建产物和前端依赖等文件 |
| `.DS_Store` | macOS 目录元数据，不属于工程资产，可以忽略或清理 |

当前目录没有 `.git`，因此它目前不是一个独立 Git 仓库。

## 4. Java 后端模块

### 4.1 基础模块

| 模块 | 作用 |
| --- | --- |
| `tpip-bom` | 统一依赖版本清单，可供平台模块、外部工程和 SDK 使用 |
| `tpip-shared-kernel` | 最小共享内核，包括资产编码、语义版本等稳定值对象 |
| `tpip-contract-api` | 标准调用契约、请求响应模型和错误契约，可作为调用方依赖 |

### 4.2 领域与规则模块

| 模块 | 作用 |
| --- | --- |
| `tpip-domain` | 核心领域模型、领域服务和 Repository 端口，不依赖 Spring MVC、数据库 Entity 或具体 HTTP 客户端 |
| `tpip-mapping-engine` | JSONPath 字段映射编译与执行，负责第三方字段和平台标准字段之间的转换 |
| `tpip-policy-engine` | Policy DSL 编译器和中间表示，用于替代运行时动态 Groovy 脚本 |
| `tpip-bundle` | 将契约、Mapping、Policy、Endpoint 等设计态资产编译为不可变运行 Bundle |
| `tpip-runtime-core` | Bundle 解析、标准契约校验、Mapping、Policy Hook 和 HTTP 调用运行流水线 |

`tpip-domain` 内部主要领域：

```text
catalog/       业务域、能力、操作和标准契约
provider/      第三方提供方、端点、凭证引用和提供方契约
integration/   Binding、Mapping、Policy 等集成资产
release/       Workspace、Bundle、Deployment、Verification 和漂移治理
```

### 4.3 基础设施与应用模块

| 模块 | 作用 |
| --- | --- |
| `tpip-adapters` | JDBC、Redis、HTTP、通知、归档和 S3 兼容存储等端口适配器 |
| `tpip-control-plane-app` | 控制面 Spring Boot 应用，负责资产配置、验证、编译、发布、治理和数据库迁移 |
| `tpip-runtime-app` | 运行面 Spring Boot 应用，只执行已发布 Bundle，不直接读取设计态数据库 |
| `tpip-worker-app` | 后台 Worker，负责通知、健康评估、自动化任务和全局影响任务调度 |
| `tpip-test-kit` | 测试 Fixture、辅助构造器和后续 SDK 测试能力 |

三个启动应用的职责：

```text
Control Plane
  ├── 配置和治理设计态资产
  ├── 验证、编译和发布 Bundle
  ├── Verification 与漂移治理
  └── MySQL/Flyway

Runtime
  ├── 解析已发布 Bundle
  ├── 执行契约、Mapping 和 Policy
  └── 调用第三方 HTTP 接口

Worker
  ├── 异步通知
  ├── 自动健康治理
  ├── 定时回归
  └── 全局影响任务处理
```

### 4.4 推荐依赖方向

```text
shared-kernel / contract-api
          ↓
        domain
          ↓
mapping-engine / policy-engine
          ↓
        bundle
          ↓
     runtime-core
          ↓
       adapters
          ↓
control-plane-app / runtime-app / worker-app
```

领域模块不能反向依赖应用层或基础设施适配器。

## 5. 前端工程

`tpip-ui` 是本地单用户第三方接入配置与运营治理工作台，使用 Vue 3、TypeScript、Vite、Element Plus、Vue Router、TanStack Vue Query 和 Pinia。

```text
tpip-ui/
├── package.json                 依赖和 pnpm 脚本
├── pnpm-lock.yaml               精确依赖锁文件
├── pnpm-workspace.yaml          pnpm 安全构建策略
├── vite.config.ts               Vite、代理、按需组件和分包配置
├── playwright.config.ts         浏览器 E2E 配置
├── tsconfig*.json               TypeScript 配置
├── index.html                   HTML 入口
├── README.md                    前端运行说明
│
├── src/
│   ├── main.ts                  Vue 应用启动入口
│   ├── App.vue                  工作台整体布局
│   ├── router/                  页面路由
│   ├── styles/                  全局样式
│   ├── api/                     通用 HTTP 和系统健康 API
│   ├── components/              全局共享组件
│   └── features/                按业务能力组织的前端模块
│
├── tests/                       Vitest 公共测试配置
└── e2e/                         Playwright 验收用例
```

当前主要业务 Feature：

```text
src/features/integration-config/
├── api/                         Provider、CredentialRef、ProviderContract、Endpoint API
└── pages/                       接入配置首页和第三方系统配置页

src/features/global-impact/
├── api/
│   ├── globalImpactApi.ts
│   └── globalImpactCommandApi.ts
├── components/
│   ├── JobStatusTag.vue
│   ├── ImpactSummaryPanel.vue
│   └── JobCommandDialog.vue
├── model/
│   ├── presentation.ts
│   ├── summaryPresentation.ts
│   └── commandPresentation.ts
└── pages/
    ├── GlobalImpactJobListPage.vue
    └── GlobalImpactJobDetailPage.vue
```

该 Feature 当前负责：

- 全局影响任务列表、筛选和分页；
- 任务详情和恢复建议；
- Workspace 影响证据；
- 风险和执行状态摘要；
- 审计时间线；
- 调整优先级、取消、失败重试和封板等受控命令。

## 6. 数据库目录

`database/migration` 是 Flyway 数据库迁移的唯一来源，目前包含 V1 到 V40。

| 迁移范围 | 主要内容 |
| --- | --- |
| V1-V4 | 核心资产、Bundle、Deployment 和内置 Policy |
| V5-V7 | 健康评估和健康告警 |
| V8-V21 | 通知、投递、路由、可靠性和归档 |
| V22-V29 | Verification Engine、FixtureSuite、Baseline 和回归策略 |
| V30-V35 | 漂移治理工作台、Policy、执行账本和操作闭环 |
| V36-V37 | Workspace 与 Global 影响快照门禁 |
| V38-V40 | Global 影响任务、生命周期和调度治理 |

当前本地数据库 schema 版本为 V40。

迁移文件一旦进入已使用环境不得原地修改，需要通过新增下一版本迁移演进。

## 7. 本地配置目录

`config` 保存本地配置模板和本机覆盖配置。

| 文件 | 作用 |
| --- | --- |
| `application-local.example.yml` | 本地配置模板，可以进入版本管理 |
| `application-local.yml` | 本机实际覆盖配置，应被 Git 忽略，不应提交到仓库 |

数据库密码、Token 和第三方 Secret 应通过环境变量或 Secret Reference 提供，不应写入正式配置文件。

## 8. 架构与实施文档

`docs` 保存平台标准、架构设计、实施方案、ADR 和阶段验收说明。

| 分类 | 内容 |
| --- | --- |
| 总体设计 | 第三方集成平台重构、工程设计和实施计划 |
| 标准资产 | 企业外部系统集成标准、PCS 与 EA 资产体系 |
| Runtime | Bundle、执行流水线、可观测性和部署治理 |
| Notification | 通知渠道、路由、模板、投递、重试和归档 |
| Verification | FixtureSuite、Baseline、回归和漂移检测 |
| Drift Governance | 工作台、策略、执行账本、提醒和影响分析 |
| Global Impact | 全局影响任务 v1.0 到 v1.6 |
| UI | 治理工作台 UI v0.1 到 v0.4 |
| 使用手册 | 平台启动、功能、运维以及业务系统接入指南 |
| ADR | 关键架构决策记录 |

关键文档：

- `third-party-integration-platform-redesign.md`：平台重新设计；
- `third-party-integration-platform-engineering-design.md`：工程落地设计；
- `third-party-integration-platform-implementation-plan.md`：实施计划；
- `enterprise-external-system-integration-standard.md`：企业外部系统集成标准；
- `tpip-platform-user-manual.md`：平台功能说明与使用手册；
- `tpip-business-system-integration-guide.md`：业务系统接入和 Runtime 调用指南；
- `adr/ADR-001-ui-technology-stack.md`：UI 技术选型决策。

## 9. 端到端验收目录

`e2e/customer-lookup` 保存第三方接入本地端到端验收场景；`e2e/ui-governance-workbench` 保存治理 UI 的隔离夹具、
可回滚自动化门禁和清理校验；`e2e/release-evidence` 保存本地发布封板、版本化 Schema 和独立证据校验器。

```text
e2e/customer-lookup/
├── README.md
├── bootstrap.sh
├── finish-bootstrap.sh
├── deploy-and-verify.sh
├── verify-rollback.sh
├── mock-provider.py
└── evidence/
```

主要职责：

- 启动 Mock 第三方服务；
- 初始化 Provider、Contract、Mapping、Policy 和 Binding 等资产；
- 编译、发布和激活 Bundle；
- 执行 Runtime 调用；
- 验证 Mapping、Policy 和回滚；
- 保存每个阶段的接口响应和验收摘要。

`evidence/时间戳/` 是验收证据目录，不是业务源代码。

```text
e2e/ui-governance-workbench/
├── UiGovernanceFixture.java    MySQL 隔离资产 Seed/Cleanup/Verify
├── fixture.sh                  JDK 21 夹具执行入口
├── generate-report.mjs         JSON/Markdown 门禁报告生成器
├── verify.sh                   构建、启停、Playwright 与 finally 清理编排
└── README.md                   门禁使用和安全边界
```

浏览器业务步骤位于 `tpip-ui/e2e/reminder-batch-audit-gate.spec.ts`，与 shell 生命周期编排分离；
`generate-report.mjs` 在隔离数据完成清理后，将业务证据和运行结果合成为版本化 JSON/Markdown 门禁报告。

```text
e2e/release-evidence/
├── schema/release-evidence-v1.schema.json  发布证据 JSON Schema
├── generate-release-evidence.mjs           制品索引、checksum 与摘要生成
├── verify-release-evidence.mjs             独立重算与封板前置条件校验
├── seal-release.sh                          测试、构建、门禁、归档和封板编排
└── README.md                                状态语义和使用说明
```

## 10. 运行数据目录

`var` 用于保存本地运行产生的数据。

```text
var/
└── archive/
    └── notification-attempts/
```

当前主要保存本地通知投递归档。生产环境应替换为具备 Versioning、Object Lock 或 WORM 能力的对象存储。

## 11. 构建生成目录

以下目录是可重新生成的构建或依赖产物，不属于需要人工维护的工程资产：

```text
各 Java 模块/target/     Maven 编译、测试和打包产物
tpip-ui/node_modules/    前端依赖
tpip-ui/dist/            前端生产构建产物
.pnpm-store/             本地 pnpm 缓存
```

## 12. 当前可整理项

- `tpip/` 当前为空，可以删除，也可以明确规划为未来聚合发行目录；
- `.DS_Store` 是 macOS 临时文件，不应作为工程资产；
- 当前根目录缺少 `.git`，如准备进行正式版本管理，需要确认上层仓库边界或初始化独立仓库；
- 新增模块时应同步更新父 `pom.xml`、根 `README.md` 和本文档；
- 新增数据库能力时应只追加 Flyway 版本，不修改已执行迁移；
- 新增前端业务能力应优先放入 `src/features/<feature-name>`，避免持续堆积到全局组件目录。

## 13. 维护规则

1. 目录结构发生实质变化时同步更新本文档。
2. 新增 Maven 模块必须说明职责、依赖方向和启动属性。
3. 新增前端 Feature 必须包含 API、模型、页面/组件和测试边界。
4. E2E 证据按时间戳归档，不覆盖历史证据。
5. 本地配置和运行数据不得混入设计态资产或源码目录。
6. Secret 原文不得进入代码、文档、数据库设计态资产或前端构建产物。

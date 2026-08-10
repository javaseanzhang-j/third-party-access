# OIDC/JWT 与 RBAC 延期待办

## 1. 决策

状态：`DEFERRED / ON_DEMAND`。

TPIP 当前定位为本地单机、单用户的架构验证和工程建设环境，暂不实施 OIDC、JWT Resource Server、RBAC、服务账号 Client Credentials 和外部身份平台集成。

该延期不否定既定安全架构，只是不把认证授权作为当前本地研发的前置门禁。出现真实多人协作、共享部署或外部 Auth 环境后，再按需恢复实施。

当前 UI 同样采用本地单用户模式：不建设登录页、会话管理、用户管理、角色管理或前端路由权限。UI 直接访问本机 Control Plane；管理写操作仍显式填写或由本地默认值提供 `X-Operator` 审计标签，但该标签不具备认证效力。

## 2. 当前临时身份策略

- 管理写接口继续使用 `X-Operator` 记录本地操作人；
- Worker 内部接口继续使用现有静态 Automation Token；
- `X-Operator` 只作为本地审计标签，不视为可信身份认证；
- 不基于 `X-Operator` 实现审批隔离或安全授权；
- 自动归档和在线证据清理继续默认关闭。

## 3. 本地运行安全边界

延期期间必须满足：

- Control Plane、Runtime、Worker、MySQL、Redis只绑定本机或受控容器网络；
- 不将服务端口暴露到公网、办公网或不可信局域网；
- 不配置公网端口映射、反向代理或外网隧道；
- 不接入真实生产Secret、个人敏感数据或受监管数据；
- 本地Automation Token不得复用于其他环境；
- UI 开发服务器和构建产物只能通过 loopback 地址访问，不配置局域网监听；
- 浏览器持久化存储中不得保存 Automation Token、数据库口令或第三方 Secret；
- `TPIP_NOTIFICATION_ATTEMPT_PURGE_ENABLED`保持`false`；
- 本地文件归档目录仅供当前用户访问；
- 一旦变为多人或远程访问环境，必须重新评审本决策。

## 4. 恢复实施的触发条件

出现以下任一条件时，OIDC/JWT与RBAC从待办恢复为必需项：

- TPIP部署到共享开发、测试、预生产或生产环境；
- 其他用户、团队或系统能够访问控制面；
- 出现企业统一身份平台、Keycloak或其他OIDC Provider；
- 管理控制台需要用户登录；
- Worker、Runtime跨主机或跨网络访问Control Plane；
- 接入真实Secret、生产第三方账号或敏感业务数据；
- 启用归档自动化、在线证据清理、法律保全或双人审批；
- 需要区分开发、发布、运维、审批和审计职责；
- 需要满足安全审计、等保或合规要求。

## 5. 恢复后的目标方案

恢复时按以下顺序实施：

1. Control Plane与Runtime接入OAuth2 Resource Server JWT校验；
2. 从Token提取`ActorContext`并移除对`X-Operator`的信任；
3. 建立角色、权限和`GLOBAL/ENVIRONMENT`作用域；
4. Worker与Runtime改用Client Credentials服务身份；
5. 启用方法级权限和未声明接口默认拒绝；
6. 再建设双人清理审批和职责分离。

建议标准角色仍保留：

```text
TPIP_VIEWER
TPIP_DEVELOPER
TPIP_PUBLISHER
TPIP_OPERATOR
TPIP_APPROVER
TPIP_ARCHIVE_REVIEWER
TPIP_LEGAL_HOLD_ADMIN
TPIP_PURGE_APPROVER
TPIP_PURGE_EXECUTOR
TPIP_AUDITOR
TPIP_SECURITY_ADMIN
TPIP_PLATFORM_ADMIN
TPIP_SERVICE_WORKER
TPIP_SERVICE_RUNTIME
```

## 6. 影响

延期后不实施原计划 v0.15 的认证授权代码和认证专用数据库迁移；当前业务与治理迁移已独立推进至 V40。后续优先建设不依赖多人身份体系的本地核心能力；任何需要可信身份、职责分离或外部访问的功能只能完成设计和默认关闭的工程骨架，不能声明为生产可用。

对 UI 的具体影响：

- UI 路由和页面不依赖角色判断，操作能力以服务端返回的资源状态和 `allowedActions` 为准；
- 前端 API Client 保留可插拔的认证请求增强接口，当前实现为空，不生成伪造 JWT；
- 后端 DTO 不绑定用户表或角色表，审计操作者继续使用独立的 Actor/Operator 字段；
- 未来启用 OIDC 时追加登录入口、Token 生命周期处理和路由权限，不重写业务页面与领域 API。

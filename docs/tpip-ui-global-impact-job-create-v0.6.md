# TPIP UI 全局影响任务创建 v0.6

## 1. 目标

在本地无登录治理工作台中提供受控的 Global 影响任务创建入口。操作人选择已有 Global 治理策略和候选版本，
设置证据有效期，创建后进入任务详情继续观察 Worker 计算与人工封板。

## 2. 用户流程

```text
任务列表
  -> 创建影响任务
  -> 选择 Global 策略
  -> 选择 DRAFT / PUBLISHED 版本
  -> 设置 TTL（5 分钟—24 小时）
  -> 确认冻结范围语义
  -> 创建 PENDING 任务
  -> 跳转任务详情
```

## 3. 边界与安全性

- 策略列表在前端再次过滤 `scope = GLOBAL`。
- 候选版本只展示 `DRAFT` 或 `PUBLISHED`。
- TTL 与服务端契约保持一致：300—86400 整数秒。
- 创建命令携带 `X-Operator`，默认本地身份为 `local-ui`。
- 创建只冻结 Workspace 覆盖与当前策略基线，不启动 Worker，不自动重试、封板、发布或激活。
- 提交中禁止重复操作；服务端错误以可见信息保留在当前页面。
- 不引入登录、JWT 或虚构权限逻辑。

## 4. 工程实现

- 路由：`/global-impact-jobs/new`
- 页面：`GlobalImpactJobCreatePage.vue`
- API：`globalImpactCreationApi.ts`
- 校验：`createJobValidation.ts`
- 入口：任务列表右上角“创建影响任务”按钮

创建成功后失效任务列表查询缓存，并导航到 `/global-impact-jobs/{jobId}`。

## 5. 验收结果

```text
Test Files  12 passed (12)
Tests       21 passed (21)
vue-tsc     passed
vite build  passed
创建页异步 chunk 6.44 kB（gzip 2.97 kB）
最大 JS chunk 427.23 kB（gzip 145.69 kB）
```

Playwright 已登记创建页入口场景；本阶段未启动浏览器执行。

## 6. 下一阶段建议

UI v0.7 建设任务运行控制与运营提示：显式启动/调度引导、失败项筛选与诊断、临期提示、READY 封板前检查摘要。
Worker 仍保持独立部署与默认关闭，UI 不直接模拟 Worker 身份。


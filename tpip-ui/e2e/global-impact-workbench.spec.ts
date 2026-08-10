import { expect, test } from '@playwright/test'

test('opens the local global impact workbench', async ({ page }) => {
  await page.goto('/global-impact-jobs')
  await expect(page.getByRole('heading', { name: '全局影响任务' })).toBeVisible()
  await expect(page.getByText('本地单用户')).toBeVisible()
})

test('opens the controlled global impact job creation page', async ({ page }) => {
  await page.goto('/global-impact-jobs/new')
  await expect(page.getByRole('heading', { name: '创建全局影响任务' })).toBeVisible()
  await expect(page.getByText('创建本身不会启动 Worker')).toBeVisible()
  await expect(page.getByRole('button', { name: '创建影响任务' })).toBeVisible()
})

test('opens the global impact operations overview', async ({ page }) => {
  await page.goto('/global-impact-operations')
  await expect(page.getByRole('heading', { name: '全局运营态势' })).toBeVisible()
  await expect(page.getByText('Critical 阈值')).toBeVisible()
})

test('opens the governance policy asset explorer', async ({ page }) => {
  await page.goto('/governance-policies')
  await expect(page.getByRole('heading', { name: '治理策略资产' })).toBeVisible()
  await expect(page.getByText('浏览 Global 与 Workspace 治理策略')).toBeVisible()
})

test('opens the Workspace asset explorer', async ({ page }) => {
  await page.goto('/workspaces')
  await expect(page.getByRole('heading', { name: 'Workspace 资产' })).toBeVisible()
  await expect(page.getByText('有效治理策略')).toBeVisible()
})

test('opens the drift governance workbench', async ({ page }) => {
  await page.goto('/drift-workbench?workspaceId=23')
  await expect(page.getByRole('heading', { name: '漂移治理工作台' })).toBeVisible()
  await expect(page.getByText('基于 Row Version 先 Dry Run')).toBeVisible()
})

test('opens the governance operation evidence lookup', async ({ page }) => {
  await page.goto('/drift-operations')
  await expect(page.getByRole('heading', { name: '治理操作证据' })).toBeVisible()
  await expect(page.getByPlaceholder('输入 Command Key')).toBeVisible()
})

test('opens the drift governance operations metrics', async ({ page }) => {
  await page.goto('/drift-governance-metrics')
  await expect(page.getByRole('heading', { name: '治理运营度量' })).toBeVisible()
  await expect(page.getByText('分析覆盖 SLA')).toBeVisible()
})

test('opens the drift governance evaluation assets', async ({ page }) => {
  await page.goto('/drift-governance-evaluations')
  await expect(page.getByRole('heading', { name: '治理评估与执行资产' })).toBeVisible()
  await expect(page.getByText('只读评估不会物化执行账本')).toBeVisible()
})

test('opens the reminder batch operations assets', async ({ page }) => {
  await page.goto('/drift-reminder-batches')
  await expect(page.getByRole('heading', { name: '提醒批次运营资产' })).toBeVisible()
  await expect(page.getByText('所有命令使用服务端状态机')).toBeVisible()
})

test('navigates from a sealed job to its immutable snapshot asset', async ({ page }) => {
  const jobId = process.env.TPIP_E2E_GLOBAL_IMPACT_JOB_ID
  const snapshotId = process.env.TPIP_E2E_GLOBAL_IMPACT_SNAPSHOT_ID
  test.skip(!jobId || !snapshotId, 'requires IDs produced by e2e/global-impact-seal/verify.sh')

  await page.goto(`/global-impact-jobs/${jobId}`)
  await expect(page.getByRole('heading', { name: /任务/ })).toBeVisible()
  await page.getByRole('button', { name: '查看封板快照' }).click()

  await expect(page).toHaveURL(new RegExp(`/global-impact-snapshots/${snapshotId}$`))
  await expect(page.getByRole('heading', { name: /封板快照/ })).toBeVisible()
  await expect(page.getByText('不可变治理证据')).toBeVisible()
  await expect(page.getByText('消费状态')).toBeVisible()
})

import { expect, test, type Page } from '@playwright/test'
import { createHash } from 'node:crypto'
import { writeFileSync } from 'node:fs'

const workspaceId = Number(process.env.TPIP_E2E_UI_WORKSPACE_ID)
const base = '/control/v1/verification-drift-workbench/governance-reminder-batches'

interface AuditEvent {
  eventId: string
  eventType: string
  rowVersion: number | null
  reason: string | null
  replacesBatchId: number | null
  replacedByBatchId: number | null
  outboxId: number | null
}
interface Timeline { currentRowVersion: number; events: AuditEvent[] }
interface BatchDetail {
  batch: { id: number; rowVersion: number; outboxId: number | null
    replacesBatchId: number | null; replacedByBatchId: number | null }
}
interface DeliveryStatus { submitted: boolean; overview: { outboxId: number } | null }

interface AuditChainEvidence {
  batchId: number
  currentRowVersion: number
  events: AuditEvent[]
}

async function readJson<T>(page: Page, path: string): Promise<T> {
  const response = await page.request.get(path)
  expect(response.ok(), `${path} returned ${response.status()}`).toBeTruthy()
  return response.json() as Promise<T>
}

function idFrom(text: string | null, pattern: RegExp): number {
  const match = text?.match(pattern)
  if (!match?.[1]) throw new Error(`Cannot read asset id from: ${text}`)
  return Number(match[1])
}

function assertEventIntegrity(timeline: Timeline, types: string[], versions: number[]): void {
  expect(timeline.events.map(event => event.eventType)).toEqual(types)
  expect(timeline.events.map(event => event.rowVersion)).toEqual(versions)
  expect(new Set(timeline.events.map(event => event.eventId)).size).toBe(timeline.events.length)
  expect(timeline.currentRowVersion).toBe(versions.at(-1))
}

function writeGovernanceEvidence(
  originalId: number,
  replacementId: number,
  outboxId: number,
  originalTimeline: Timeline,
  replacementTimeline: Timeline
): void {
  const resultPath = process.env.TPIP_E2E_GATE_RESULT_PATH
  if (!resultPath) return

  const canonicalEvents = (events: AuditEvent[]): AuditEvent[] => events.map(event => ({
    eventId: event.eventId,
    eventType: event.eventType,
    rowVersion: event.rowVersion,
    reason: event.reason,
    replacesBatchId: event.replacesBatchId,
    replacedByBatchId: event.replacedByBatchId,
    outboxId: event.outboxId
  }))
  const chains: AuditChainEvidence[] = [
    { batchId: originalId, currentRowVersion: originalTimeline.currentRowVersion,
      events: canonicalEvents(originalTimeline.events) },
    { batchId: replacementId, currentRowVersion: replacementTimeline.currentRowVersion,
      events: canonicalEvents(replacementTimeline.events) }
  ]
  const canonicalChain = JSON.stringify(chains)
  const auditChainChecksum = createHash('sha256').update(canonicalChain).digest('hex')
  const evidence = {
    schemaVersion: '1.0',
    evidenceType: 'TPIP_REMINDER_BATCH_GOVERNANCE',
    runId: process.env.TPIP_E2E_GATE_RUN_ID ?? null,
    workspaceId,
    assets: { originalBatchId: originalId, replacementBatchId: replacementId, outboxId },
    auditChains: chains,
    auditChainChecksum: { algorithm: 'SHA-256', value: auditChainChecksum },
    assertions: {
      immutableEventIds: true,
      contiguousRowVersions: true,
      bidirectionalReplacementLineage: true,
      governanceReasonRecorded: true,
      outboxProjectionConsistent: true
    }
  }
  writeFileSync(resultPath, `${JSON.stringify(evidence, null, 2)}\n`, { encoding: 'utf8', flag: 'wx' })
}

test('governs reminder batches with complete immutable audit evidence', async ({ page }) => {
  test.skip(!Number.isInteger(workspaceId) || workspaceId <= 0,
    'requires TPIP_E2E_UI_WORKSPACE_ID from the isolated governance fixture')
  test.setTimeout(60_000)

  await page.goto(`/drift-reminder-batches?workspaceId=${workspaceId}`)
  await expect(page.getByRole('heading', { name: '提醒批次运营资产' })).toBeVisible()
  await expect(page.getByRole('button', { name: 'Control Plane 正常' })).toBeVisible()

  const draftRow = page.getByRole('row').filter({ hasText: 'DRAFT' }).first()
  await draftRow.getByRole('button', { name: '查看资产' }).click()
  let drawer = page.getByRole('dialog', { name: '提醒批次资产详情' })
  await expect(drawer.getByText('没有可读取的不可变审计事件')).toBeVisible()
  await drawer.getByRole('button', { name: '取消', exact: true }).click()
  let command = page.getByRole('dialog', { name: '取消提醒批次' })
  await command.getByRole('textbox', { name: '说明本次治理操作依据' })
    .fill('v0.19 release isolated fixture reservation')
  await command.getByRole('button', { name: '确认执行' }).click()
  await expect(drawer.getByText('取消批次')).toBeVisible()
  await expect(drawer.getByText('依据：v0.19 release isolated fixture reservation')).toBeVisible()
  await drawer.getByRole('button', { name: 'Close this dialog' }).click()

  const candidate = page.locator('.reminder-candidate-grid .el-checkbox').first()
  await expect(candidate).toBeVisible()
  await candidate.click()
  await page.getByRole('button', { name: '检查并创建' }).click()
  const createDialog = page.getByRole('dialog', { name: '确认创建 DRAFT 批次' })
  await createDialog.getByRole('button', { name: '确认创建 DRAFT' }).click()
  const createdNotice = page.getByRole('alert').filter({ hasText: 'DRAFT 批次 #' })
  await expect(createdNotice).toBeVisible()
  const originalId = idFrom(await createdNotice.textContent(), /DRAFT 批次 #(\d+)/)
  drawer = page.getByRole('dialog', { name: '提醒批次资产详情' })
  await expect(drawer.getByText('创建 DRAFT')).toBeVisible()
  await expect(drawer.getByText('RV 0', { exact: true })).toBeVisible()

  await drawer.getByRole('button', { name: '替代', exact: true }).click()
  command = page.getByRole('dialog', { name: '替代提醒批次' })
  await command.getByRole('textbox', { name: '说明本次治理操作依据' })
    .fill('v0.19 replacement lineage integrity gate')
  await command.getByRole('button', { name: '确认执行' }).click()
  const replacementNotice = page.getByRole('alert').filter({ hasText: '替代批次 #' })
  await expect(replacementNotice).toBeVisible()
  const replacementId = idFrom(await replacementNotice.textContent(), /替代批次 #(\d+)/)
  expect(replacementId).not.toBe(originalId)

  const originalTimeline = await readJson<Timeline>(page, `${base}/${originalId}/timeline`)
  assertEventIntegrity(originalTimeline, [
    'DRIFT_GOVERNANCE_REMINDER_BATCH_CREATED',
    'DRIFT_GOVERNANCE_REMINDER_BATCH_CANCELLED',
    'DRIFT_GOVERNANCE_REMINDER_BATCH_REPLACEMENT_LINKED'
  ], [0, 1, 2])
  expect(originalTimeline.events[1]?.reason).toBe('v0.19 replacement lineage integrity gate')
  expect(originalTimeline.events[2]?.replacedByBatchId).toBe(replacementId)

  const original = await readJson<BatchDetail>(page, `${base}/${originalId}`)
  const replacement = await readJson<BatchDetail>(page, `${base}/${replacementId}`)
  expect(original.batch.replacedByBatchId).toBe(replacementId)
  expect(replacement.batch.replacesBatchId).toBe(originalId)
  await expect(drawer.getByText(`替代 #${originalId}`, { exact: true })).toBeVisible()

  await drawer.getByRole('button', { name: '批准', exact: true }).click()
  command = page.getByRole('dialog', { name: '批准提醒批次' })
  await command.getByRole('button', { name: '确认执行' }).click()
  await expect(drawer.getByText('当前 RV 1 · 2 条证据')).toBeVisible()
  await drawer.getByRole('button', { name: '提交 Outbox' }).click()
  command = page.getByRole('dialog', { name: '提交提醒批次到 Outbox' })
  await command.getByText('我已核对 Workspace、环境、成员、提醒序号和预算，确认提交到 Outbox',
    { exact: true }).click()
  await command.getByRole('button', { name: '确认执行' }).click()
  const dispatchNotice = page.getByRole('alert').filter({ hasText: '批次已提交到 Outbox #' })
  await expect(dispatchNotice).toBeVisible()
  const outboxId = idFrom(await dispatchNotice.textContent(), /Outbox #(\d+)/)

  const replacementTimeline = await readJson<Timeline>(page, `${base}/${replacementId}/timeline`)
  assertEventIntegrity(replacementTimeline, [
    'DRIFT_GOVERNANCE_REMINDER_BATCH_CREATED',
    'DRIFT_GOVERNANCE_REMINDER_BATCH_APPROVED',
    'DRIFT_GOVERNANCE_REMINDER_BATCH_DISPATCHED'
  ], [0, 1, 2])
  expect(replacementTimeline.events[0]?.replacesBatchId).toBe(originalId)
  expect(replacementTimeline.events[2]?.outboxId).toBe(outboxId)

  const dispatched = await readJson<BatchDetail>(page, `${base}/${replacementId}`)
  const delivery = await readJson<DeliveryStatus>(page, `${base}/${replacementId}/delivery-status`)
  expect(dispatched.batch.outboxId).toBe(outboxId)
  expect(dispatched.batch.rowVersion).toBe(2)
  expect(delivery.submitted).toBeTruthy()
  expect(delivery.overview?.outboxId).toBe(outboxId)
  await expect(drawer.getByText('提交 Outbox')).toBeVisible()
  await expect(drawer.getByText(`Outbox #${outboxId}`, { exact: true }).first()).toBeVisible()
  await expect(drawer.getByText('PENDING · UNROUTED')).toBeVisible()

  writeGovernanceEvidence(
    originalId, replacementId, outboxId, originalTimeline, replacementTimeline)
})

import { postJson } from '@/api/http'
import type { GovernanceExecution } from './driftGovernanceEvaluationApi'
import type { BatchDetail } from './driftReminderBatchApi'

const base = '/control/v1/verification-drift-workbench'
const operator = (import.meta.env.VITE_TPIP_OPERATOR as string | undefined)?.trim() || 'local-ui'
const headers = { 'X-Operator': operator }

export interface ReplacementResult { cancelled: BatchDetail; replacement: BatchDetail }

export const driftGovernanceCommandApi = {
  materialize: (workspaceId: number, reportId: number) =>
    postJson<GovernanceExecution>(`${base}/governance-executions:materialize`,
      { workspaceId, reportId }, headers),
  createBatch: (workspaceId: number, environmentCode: string, executionIds: number[]) =>
    postJson<BatchDetail>(`${base}/governance-reminder-batches`,
      { workspaceId, environmentCode, executionIds }, headers),
  approveBatch: (batchId: number, rowVersion: number) =>
    postJson<BatchDetail>(`${base}/governance-reminder-batches/${batchId}:approve`, { rowVersion }, headers),
  cancelBatch: (batchId: number, rowVersion: number, reason: string) =>
    postJson<BatchDetail>(`${base}/governance-reminder-batches/${batchId}:cancel`,
      { rowVersion, reason }, headers),
  replaceBatch: (batchId: number, rowVersion: number, reason: string,
    environmentCode: string, executionIds: number[]) =>
    postJson<ReplacementResult>(`${base}/governance-reminder-batches/${batchId}:replace`,
      { rowVersion, reason, environmentCode, executionIds }, headers),
  dispatchBatch: (batchId: number, rowVersion: number) =>
    postJson<BatchDetail>(`${base}/governance-reminder-batches/${batchId}:dispatch`, { rowVersion }, headers)
}

import { getJson } from '@/api/http'
import type { JobPriority, JobStatus } from './globalImpactApi'

export type StallSeverity = 'WARNING' | 'CRITICAL'
export interface OperationsCandidatePolicy {
  policyId: number; policyCode: string; policyName: string; versionId: number; versionNo: number
}
export interface OperationsProgress {
  workspaceCount: number; pendingCount: number; runningCount: number
  succeededCount: number; failedCount: number; progressPercent: number
}
export interface OperationsItem {
  jobId: string; status: JobStatus; priority: JobPriority
  candidatePolicy: OperationsCandidatePolicy; progress: OperationsProgress
  expiresAt: string; expired: boolean; rowVersion: number; dispatchCount: number
  lastDispatchedAt: string | null; lastProgressAt: string
  dispatchLeaseOwner: string | null; dispatchLeaseUntil: string | null
  stalledSeconds: number; severity: StallSeverity; recoveryRecommendation: string
}
export interface OperationsOverview {
  generatedAt: string; stallThresholdSeconds: number; criticalThresholdSeconds: number
  stalledCount: number; criticalCount: number; warningCount: number
  recoveryRecommendations: { recommendation: string; count: number }[]
  items: OperationsItem[]; limitReached: boolean
}

const base = '/control/v1/verification-drift-workbench/global-governance-policy-impact-operations-view'
export const globalImpactOperationsApi = {
  overview: (limit = 100, signal?: AbortSignal) =>
    getJson<OperationsOverview>(`${base}?limit=${limit}`, signal)
}

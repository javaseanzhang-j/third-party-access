import { getJson } from '@/api/http'
import type { DriftKind, ReviewStatus } from './driftWorkbenchApi'

const base = '/control/v1/verification-drift-workbench'

export interface PolicySnapshot {
  source: 'WORKSPACE_POLICY' | 'GLOBAL_POLICY' | 'BUILT_IN_DEFAULT'
  policyId: number | null
  policyVersionId: number | null
  overdueAfterSeconds: number
  aggregationWindowSeconds: number
  reminderIntervalSeconds: number
  maximumReminders: number
  ownerCode: string
  suppressedDriftKinds: DriftKind[]
  suppressedCheckCodes: string[]
}
export interface DriftEvaluation {
  itemNo: number
  checkCode: string
  driftKind: DriftKind
  suppressed: boolean
  kindSuppressed: boolean
  checkSuppressed: boolean
}
export interface ReportEvaluation {
  reportId: number
  reviewStatus: ReviewStatus
  rowVersion: number
  createdAt: string
  dueAt: string
  overdue: boolean
  fullySuppressed: boolean
  reminderCandidate: boolean
  drifts: DriftEvaluation[]
}
export interface EvaluationPage {
  policy: PolicySnapshot
  items: ReportEvaluation[]
  page: number
  size: number
  totalElements: number
}
export interface GovernanceExecution {
  id: number
  driftReportId: number
  workspaceId: number
  policySource: PolicySnapshot['source']
  policyId: number | null
  policyVersionId: number | null
  aggregationKey: string
  ownerCode: string
  status: 'READY' | 'EXHAUSTED' | 'CLOSED'
  maximumReminders: number
  reminderIntervalSeconds: number
  reminderCount: number
  nextReminderAt: string
  evaluationChecksum: string
  rowVersion: number
  materializedBy: string
  materializedAt: string | null
  updatedAt: string | null
}

export function getGovernanceEvaluations(workspaceId: number, page = 0, size = 20,
  signal?: AbortSignal): Promise<EvaluationPage> {
  const query = new URLSearchParams({ workspaceId: String(workspaceId), page: String(page), size: String(size) })
  return getJson<EvaluationPage>(`${base}/governance-evaluations?${query}`, signal)
}

export function getGovernanceExecutions(workspaceId: number, signal?: AbortSignal): Promise<GovernanceExecution[]> {
  return getJson<GovernanceExecution[]>(`${base}/governance-executions?workspaceId=${workspaceId}`, signal)
}

export function getDueGovernanceExecutions(workspaceId: number, signal?: AbortSignal): Promise<GovernanceExecution[]> {
  return getJson<GovernanceExecution[]>(`${base}/governance-executions:due?workspaceId=${workspaceId}&limit=100`, signal)
}

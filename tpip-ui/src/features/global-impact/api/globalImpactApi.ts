import { getJson } from '@/api/http'

export type JobStatus = 'PENDING' | 'RUNNING' | 'FAILED' | 'READY' | 'SEALED' | 'EXPIRED' | 'CANCELLED'
export type JobPriority = 'LOW' | 'NORMAL' | 'HIGH' | 'CRITICAL'
export type ItemStatus = 'PENDING' | 'RUNNING' | 'SUCCEEDED' | 'FAILED'
export type RiskLevel = 'LOW' | 'MEDIUM' | 'HIGH' | 'CRITICAL'

export interface OperationCapability { action: string; enabled: boolean; disabledReasonCode: string | null }
export interface CandidatePolicy { policyId: number; policyCode: string; policyName: string; versionId: number; versionNo: number; versionStatus: string }
export interface JobProgress { workspaceCount: number; pendingCount: number; runningCount: number; succeededCount: number; failedCount: number; processedCount: number; progressPercent: number }
export interface JobListItem {
  jobId: string; status: JobStatus; priority: JobPriority; candidatePolicy: CandidatePolicy
  progress: JobProgress; expiresAt: string; expired: boolean; rowVersion: number
  lastProgressAt: string; allowedActions: OperationCapability[]
}
export interface JobPage { items: JobListItem[]; page: number; size: number; totalElements: number; totalPages: number; hasNext: boolean }
export interface JobDetail {
  summary: JobListItem; candidateChecksum: string; coverageChecksum: string; snapshotAt: string
  sealedSnapshotId: string | null; dispatchCount: number; lastDispatchedAt: string | null
  lastProgressAt: string; dispatchLeaseOwner: string | null; dispatchLeaseUntil: string | null
  stalledSeconds: number; recoveryRecommendation: string; createdBy: string; createdAt: string
  updatedBy: string; updatedAt: string
}
export interface CurrentPolicy { policyId: number | null; policyCode: string | null; policyName: string | null; versionId: number | null; versionNo: number | null; checksum: string | null }
export interface ParameterChanges { overdueAfterSecondsDelta: number; aggregationWindowSecondsDelta: number; reminderIntervalSecondsDelta: number; maximumRemindersDelta: number; ownerChanged: boolean; suppressedDriftKindsChanged: boolean; suppressedCheckCodesChanged: boolean }
export interface ImpactSummary { actionableReports: number; currentOverdueReports: number; candidateOverdueReports: number; newlyOverdueReports: number; noLongerOverdueReports: number; currentSuppressedReports: number; candidateSuppressedReports: number; newlySuppressedReports: number; noLongerSuppressedReports: number; currentReminderCandidates: number; candidateReminderCandidates: number; addedReminderCandidates: number; removedReminderCandidates: number }
export interface ImpactComparison { snapshotId: string; impactChecksum: string; expiresAt: string; parameterChanges: ParameterChanges; summary: ImpactSummary; totalChangedReports: number }
export interface WorkspaceImpactItem {
  workspaceId: number; workspaceCode: string; workspaceName: string; environmentCode: string
  riskLevel: RiskLevel; itemOrder: number; currentPolicy: CurrentPolicy; status: ItemStatus
  attemptCount: number; leaseOwner: string | null; leaseUntil: string | null; failureCode: string | null
  failureMessage: string | null; startedAt: string | null; finishedAt: string | null
  impactComparison: ImpactComparison | null
}
export interface WorkspaceImpactPage { jobId: string; items: WorkspaceImpactItem[]; page: number; size: number; totalElements: number; totalPages: number; hasNext: boolean }
export interface WorkspaceImpactSummary {
  jobId: string; workspaceCount: number
  risks: { low: number; medium: number; high: number; critical: number }
  statuses: { pending: number; running: number; succeeded: number; failed: number }
  impactTotals: { changedReports: number; newlyOverdueReports: number; addedReminderCandidates: number }
}
export interface TimelineEntry { sequence: number; eventId: string; eventType: string; actorCode: string; summary: string; detail: string | null; occurredAt: string }
export interface Timeline { jobId: string; items: TimelineEntry[]; truncated: boolean }
export interface WorkspaceImpactFilters {
  status?: ItemStatus; riskLevel?: RiskLevel; keyword?: string; page: number; size: number
}

const base = '/control/v1/verification-drift-workbench/global-governance-policy-impact-job-views'

function query(parameters: Record<string, string | number | undefined>): string {
  const search = new URLSearchParams()
  Object.entries(parameters).forEach(([key, value]) => {
    if (value !== undefined && value !== '') search.set(key, String(value))
  })
  const value = search.toString()
  return value ? `?${value}` : ''
}

export interface JobFilters { status?: string; priority?: string; keyword?: string; page: number; size: number }
export const globalImpactApi = {
  jobs: (filters: JobFilters, signal?: AbortSignal) => getJson<JobPage>(`${base}${query({
    status: filters.status, priority: filters.priority, keyword: filters.keyword,
    page: filters.page, size: filters.size
  })}`, signal),
  job: (jobId: string, signal?: AbortSignal) => getJson<JobDetail>(`${base}/${encodeURIComponent(jobId)}`, signal),
  impacts: (jobId: string, filters: WorkspaceImpactFilters, signal?: AbortSignal) =>
    getJson<WorkspaceImpactPage>(`${base}/${encodeURIComponent(jobId)}/workspace-impacts${query({
      status: filters.status, riskLevel: filters.riskLevel, keyword: filters.keyword,
      page: filters.page, size: filters.size
    })}`, signal),
  impactSummary: (jobId: string, signal?: AbortSignal) =>
    getJson<WorkspaceImpactSummary>(`${base}/${encodeURIComponent(jobId)}/impact-summary`, signal),
  timeline: (jobId: string, signal?: AbortSignal) =>
    getJson<Timeline>(`${base}/${encodeURIComponent(jobId)}/timeline?limit=100`, signal)
}

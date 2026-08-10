import { getJson } from '@/api/http'
import type { CandidatePolicy, CurrentPolicy, ImpactSummary, ParameterChanges, RiskLevel } from './globalImpactApi'

export interface SnapshotConsumption {
  publishUsedBy: string | null; publishUsedAt: string | null
  activationUsedBy: string | null; activationUsedAt: string | null
}
export interface SnapshotDetail {
  snapshotId: string; candidatePolicy: CandidatePolicy; candidateChecksum: string
  coverageChecksum: string; impactChecksum: string; workspaceCount: number
  createdBy: string; createdAt: string; expiresAt: string; expired: boolean
  consumption: SnapshotConsumption
}
export interface SnapshotImpact {
  parameterChanges: ParameterChanges; summary: Pick<ImpactSummary,
    'actionableReports' | 'currentOverdueReports' | 'candidateOverdueReports' |
    'newlyOverdueReports' | 'noLongerOverdueReports' | 'currentReminderCandidates' |
    'candidateReminderCandidates' | 'addedReminderCandidates' | 'removedReminderCandidates'>
  totalChangedReports: number
}
export interface WorkspaceSnapshot {
  snapshotId: string; workspaceId: number; workspaceCode: string; workspaceName: string
  environmentCode: string; riskLevel: RiskLevel; currentPolicy: CurrentPolicy | null
  impactChecksum: string; expiresAt: string; impact: SnapshotImpact
}
export interface WorkspaceSnapshotPage {
  snapshotId: string; items: WorkspaceSnapshot[]; page: number; size: number
  totalElements: number; totalPages: number; hasNext: boolean
}

const base = '/control/v1/verification-drift-workbench/global-governance-policy-impact-snapshot-views'
export const globalImpactSnapshotApi = {
  snapshot: (snapshotId: string, signal?: AbortSignal) =>
    getJson<SnapshotDetail>(`${base}/${encodeURIComponent(snapshotId)}`, signal),
  workspaces: (snapshotId: string, page: number, size: number, signal?: AbortSignal) =>
    getJson<WorkspaceSnapshotPage>(`${base}/${encodeURIComponent(snapshotId)}/workspace-snapshots?page=${page}&size=${size}`, signal)
}

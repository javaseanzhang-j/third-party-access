import { getJson } from '@/api/http'
import type { JobPriority, JobStatus } from '@/features/global-impact/api/globalImpactApi'

export type PolicyScope = 'GLOBAL' | 'WORKSPACE'
export type PolicyStatus = 'DRAFT' | 'PAUSED' | 'ACTIVE'
export type PolicyVersionStatus = 'DRAFT' | 'PUBLISHED'

export interface WorkspaceAssignment {
  workspaceId: number; workspaceCode: string; workspaceName: string; environmentCode: string
}
export interface CurrentVersion { versionId: number; versionNo: number; lifecycleStatus: PolicyVersionStatus }
export interface PolicyAsset {
  policyId: number; policyCode: string; policyName: string; scope: PolicyScope
  workspace: WorkspaceAssignment | null; status: PolicyStatus; currentVersion: CurrentVersion | null
  versionCount: number; impactJobCount: number; rowVersion: number; createdAt: string; updatedAt: string
}
export interface VersionAsset {
  versionId: number; policyId: number; versionNo: number; overdueAfterSeconds: number
  aggregationWindowSeconds: number; reminderIntervalSeconds: number; maximumReminders: number
  ownerCode: string; suppressedDriftKinds: string[]; suppressedCheckCodes: string[]
  contentChecksum: string; lifecycleStatus: PolicyVersionStatus; currentlySelected: boolean
  impactJobCount: number; publishedAt: string | null; createdAt: string
}
export interface PolicyImpactJob {
  jobId: string; policyId: number; versionId: number; versionNo: number; status: JobStatus
  priority: JobPriority; workspaceCount: number; succeededCount: number; failedCount: number
  progressPercent: number; sealedSnapshotId: string | null; expiresAt: string
  createdAt: string; updatedAt: string
}
export interface PolicyPage {
  items: PolicyAsset[]; page: number; size: number; totalElements: number; totalPages: number
}
export interface PolicyDetail {
  policy: PolicyAsset; versions: VersionAsset[]; recentImpactJobs: PolicyImpactJob[]
}
export interface VersionDetail {
  policy: PolicyAsset; version: VersionAsset; recentImpactJobs: PolicyImpactJob[]
}
export interface PolicyFilters {
  scope?: PolicyScope | ''; status?: PolicyStatus | ''; keyword?: string; page?: number; size?: number
}

const base = '/control/v1/verification-drift-workbench/drift-governance-policy-asset-views'

function pageQuery(filters: PolicyFilters): string {
  const query = new URLSearchParams()
  if (filters.scope) query.set('scope', filters.scope)
  if (filters.status) query.set('status', filters.status)
  if (filters.keyword?.trim()) query.set('keyword', filters.keyword.trim())
  query.set('page', String(filters.page ?? 0)); query.set('size', String(filters.size ?? 20))
  return query.toString()
}

export const governancePolicyAssetApi = {
  policies: (filters: PolicyFilters = {}, signal?: AbortSignal) =>
    getJson<PolicyPage>(`${base}?${pageQuery(filters)}`, signal),
  policy: (policyId: number, signal?: AbortSignal) =>
    getJson<PolicyDetail>(`${base}/${policyId}?recentJobLimit=20`, signal),
  version: (policyId: number, versionId: number, signal?: AbortSignal) =>
    getJson<VersionDetail>(`${base}/${policyId}/versions/${versionId}?recentJobLimit=20`, signal)
}

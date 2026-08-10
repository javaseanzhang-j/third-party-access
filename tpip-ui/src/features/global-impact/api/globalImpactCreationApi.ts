import { getJson, postJson } from '@/api/http'
import type { JobStatus } from './globalImpactApi'

export type PolicyScope = 'GLOBAL' | 'WORKSPACE'
export type PolicyStatus = 'DRAFT' | 'PAUSED' | 'ACTIVE'
export type PolicyVersionStatus = 'DRAFT' | 'PUBLISHED' | 'RETIRED'

export interface GovernancePolicy {
  id: number; policyCode: string; policyName: string; scope: PolicyScope
  workspaceId: number | null; status: PolicyStatus; currentVersionId: number | null
  rowVersion: number; createdAt: string; updatedAt: string
}
export interface GovernancePolicyVersion {
  id: number; policyId: number; versionNo: number; overdueAfterSeconds: number
  aggregationWindowSeconds: number; reminderIntervalSeconds: number; maximumReminders: number
  ownerCode: string; suppressedDriftKinds: string[]; suppressedCheckCodes: string[]
  contentChecksum: string; lifecycleStatus: PolicyVersionStatus
  publishedAt: string | null; createdAt: string
}
export interface CreatedGlobalImpactJob {
  jobId: string; candidatePolicyId: number; candidateVersionId: number
  workspaceCount: number; succeededCount: number; failedCount: number
  status: JobStatus; expiresAt: string; sealedSnapshotId: string | null; rowVersion: number
}
export interface CreateGlobalImpactJobInput {
  candidatePolicyId: number; candidateVersionId: number; ttlSeconds: number
}

const operator = (import.meta.env.VITE_TPIP_OPERATOR as string | undefined)?.trim() || 'local-ui'
const headers = { 'X-Operator': operator }

export const globalImpactCreationApi = {
  policies: (signal?: AbortSignal) => getJson<GovernancePolicy[]>('/control/v1/drift-governance-policies', signal),
  versions: (policyId: number, signal?: AbortSignal) =>
    getJson<GovernancePolicyVersion[]>(`/control/v1/drift-governance-policies/${policyId}/versions`, signal),
  create: (input: CreateGlobalImpactJobInput, signal?: AbortSignal) =>
    postJson<CreatedGlobalImpactJob>(
      '/control/v1/verification-drift-workbench/global-governance-policy-impact-jobs', input, headers, signal)
}

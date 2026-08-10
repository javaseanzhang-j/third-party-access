import { getJson, postJson } from '@/api/http'

export type WorkspaceLifecycle = 'DRAFT' | 'VERIFIED' | 'IN_REVIEW' | 'APPROVED' | 'COMPILED'
export type WorkspaceRisk = 'LOW' | 'MEDIUM' | 'HIGH' | 'CRITICAL'
export type VerificationStatus = 'RUNNING' | 'PASSED' | 'FAILED'

export interface ReleaseWorkspace {
  id: number; workspaceCode: string; workspaceName: string; baseBundleId: number | null
  environmentCode: string; lifecycleStatus: WorkspaceLifecycle; riskLevel: WorkspaceRisk
  ownerCode: string; rowVersion: number; createdAt: string; updatedAt: string
}
export interface ReleaseWorkspaceAsset {
  id: number; workspaceId: number; assetType: 'BINDING_VERSION'; assetCode: string
  assetVersionId: number; changeType: 'REFERENCE' | 'ADD' | 'MODIFY'
  dependencyMetadata: Record<string, unknown>; createdAt: string
}
export interface VerificationJob {
  id: number; workspaceId: number; runNo: number; runType: 'FULL' | 'REGRESSION'
  status: VerificationStatus; totalCount: number; passedCount: number; failedCount: number
  evidenceUri: string | null; resultSummary: Record<string, unknown>; startedAt: string; finishedAt: string | null
}
export interface VerificationCheck {
  id: number; verificationRunId: number; checkCode: string; checkName: string
  status: 'PASSED' | 'FAILED'; resultDetails: Record<string, unknown>
  evidenceDocument: Record<string, unknown>; startedAt: string; finishedAt: string
}
export interface CreateWorkspaceInput {
  workspaceCode: string; workspaceName: string; baseBundleId: null; environmentCode: string
  riskLevel: WorkspaceRisk; ownerCode: string
}

const operator = (import.meta.env.VITE_TPIP_OPERATOR as string | undefined)?.trim() || 'local-ui'
const headers = { 'X-Operator': operator }

export const workspaceVerificationApi = {
  workspaces: (signal?: AbortSignal) => getJson<ReleaseWorkspace[]>('/control/v1/workspaces', signal),
  createWorkspace: (input: CreateWorkspaceInput) => postJson<ReleaseWorkspace>('/control/v1/workspaces', input, headers),
  assets: (workspaceId: number, signal?: AbortSignal) =>
    getJson<ReleaseWorkspaceAsset[]>(`/control/v1/workspaces/${workspaceId}/assets`, signal),
  addBindingVersion: (workspaceId: number, bindingId: number, bindingVersionId: number) =>
    postJson<ReleaseWorkspaceAsset>(`/control/v1/workspaces/${workspaceId}/assets/binding-version`, {
      bindingId, bindingVersionId, changeType: 'ADD', dependencyMetadata: {}
    }, headers),
  jobs: (workspaceId: number, signal?: AbortSignal) =>
    getJson<VerificationJob[]>(`/control/v1/workspaces/${workspaceId}/verification-jobs`, signal),
  verify: (workspaceId: number, fixtureSuiteVersionId: number, rowVersion: number) =>
    postJson<VerificationJob>(`/control/v1/workspaces/${workspaceId}:verify`, { fixtureSuiteVersionId, rowVersion }, headers),
  checks: (runId: number, signal?: AbortSignal) =>
    getJson<VerificationCheck[]>(`/control/v1/verification-jobs/${runId}/checks`, signal),
  retry: (runId: number, rowVersion: number) =>
    postJson<VerificationJob>(`/control/v1/verification-jobs/${runId}:retry`, { rowVersion }, headers)
}

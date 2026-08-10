import { getJson, postJson } from '@/api/http'
import type { ReleaseWorkspace } from './workspaceVerificationApi'

export type ApprovalDecision = 'APPROVED' | 'REJECTED'
export type BundleStatus = 'READY' | 'PUBLISHED'

export interface WorkspaceApproval {
  id: number; workspaceId: number; approvalStage: 'RELEASE' | 'SECURITY'; approverCode: string
  decision: ApprovalDecision; decisionComment: string | null; evidenceSnapshot: Record<string, unknown>
  decidedAt: string
}
export interface DeploymentBundle {
  id: number; bundleCode: string; bundleVersion: string; workspaceId: number; operationId: number
  bindingVersionId: number; environmentCode: string; manifestDocument: Record<string, unknown>
  artifactUri: string; artifactChecksum: string; compilerVersion: string; runtimeCompatibility: string
  signatureMetadata: Record<string, unknown>; lifecycleStatus: BundleStatus
  publishedBy: string | null; publishedAt: string | null; createdAt: string
}

const defaultOperator = (import.meta.env.VITE_TPIP_OPERATOR as string | undefined)?.trim() || 'local-ui'
const headers = (operator = defaultOperator) => ({ 'X-Operator': operator })

export const workspaceReleaseApi = {
  submitReview: (workspaceId: number, rowVersion: number) =>
    postJson<ReleaseWorkspace>(`/control/v1/workspaces/${workspaceId}:submit-review`, { rowVersion }, headers()),
  approvals: (workspaceId: number, signal?: AbortSignal) =>
    getJson<WorkspaceApproval[]>(`/control/v1/workspaces/${workspaceId}/approvals`, signal),
  decide: (workspaceId: number, input: {
    approvalStage: 'RELEASE' | 'SECURITY'; decision: ApprovalDecision; decisionComment: string | null
    evidenceSnapshot: Record<string, unknown>; rowVersion: number; approverCode: string
  }) => postJson<WorkspaceApproval>(`/control/v1/workspaces/${workspaceId}/approvals`, {
    approvalStage: input.approvalStage, decision: input.decision, decisionComment: input.decisionComment,
    evidenceSnapshot: input.evidenceSnapshot, rowVersion: input.rowVersion
  }, headers(input.approverCode)),
  bundles: (workspaceId: number, signal?: AbortSignal) =>
    getJson<DeploymentBundle[]>(`/control/v1/workspaces/${workspaceId}/bundles`, signal),
  compile: (workspaceId: number, bundleCode: string, rowVersion: number) =>
    postJson<DeploymentBundle>(`/control/v1/workspaces/${workspaceId}/bundles`, { bundleCode, rowVersion }, headers()),
  publish: (bundleId: number) =>
    postJson<DeploymentBundle>(`/control/v1/bundles/${bundleId}:publish`, {}, headers())
}

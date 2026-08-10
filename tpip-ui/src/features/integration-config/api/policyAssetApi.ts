import { getJson, postJson } from '@/api/http'
import type { Page } from './integrationAssetApi'

export interface PolicyTypeAsset {
  id: number; policyTypeCode: string; semanticVersion: string; implementationKind: string
  allowedStages: string[]; configurationSchema: unknown; runtimeCompatibility: string
  securityClassification: string; deterministic: boolean; sideEffect: boolean
  idempotencyRequirement: string | null; implementationRef: string | null
  artifactChecksum: string | null; status: 'ACTIVE' | 'INACTIVE'; createdAt: string
}
export interface PolicyAsset {
  id: number; bindingId: number; policyCode: string; policyName: string; status: 'ACTIVE' | 'INACTIVE'
  rowVersion: number; createdAt: string; updatedAt: string
}
export interface PolicyVersionAsset {
  id: number; policyId: number; versionNo: number; dslApiVersion: string; normalizedDocument: unknown
  compilerVersion: string; compileStatus: 'COMPILED' | 'FAILED'; compileDiagnostics: unknown
  contentChecksum: string; lifecycleStatus: 'DRAFT' | 'PUBLISHED'; publishedAt: string | null; createdAt: string
}
export interface CreatePolicyInput { bindingId: number; policyCode: string; policyName: string }

const operator = (import.meta.env.VITE_TPIP_OPERATOR as string | undefined)?.trim() || 'local-ui'
const headers = { 'X-Operator': operator }
function query(parameters: Record<string, string | number | undefined>): string {
  const value = new URLSearchParams(); Object.entries(parameters).forEach(([key, item]) => { if (item !== undefined && item !== '') value.set(key, String(item)) }); return value.toString()
}
export const policyAssetApi = {
  types: (signal?: AbortSignal) => getJson<PolicyTypeAsset[]>('/control/v1/policy-types?activeOnly=true', signal),
  policies: (bindingId?: number, signal?: AbortSignal) => getJson<Page<PolicyAsset>>(
    `/control/v1/policies?${query({ bindingId, page: 0, size: 200 })}`, signal),
  createPolicy: (input: CreatePolicyInput) => postJson<PolicyAsset>('/control/v1/policies', input, headers),
  versions: (policyId: number, signal?: AbortSignal) => getJson<PolicyVersionAsset[]>(`/control/v1/policies/${policyId}/versions`, signal),
  createVersion: (policyId: number, document: unknown) => postJson<PolicyVersionAsset>(`/control/v1/policies/${policyId}/versions`, { document }, headers),
  plan: (policyId: number, versionId: number, signal?: AbortSignal) => getJson<Record<string, unknown>>(`/control/v1/policies/${policyId}/versions/${versionId}/plan`, signal),
  publishVersion: (policyId: number, versionId: number) => postJson<PolicyVersionAsset>(`/control/v1/policies/${policyId}/versions/${versionId}:publish`, {}, headers)
}

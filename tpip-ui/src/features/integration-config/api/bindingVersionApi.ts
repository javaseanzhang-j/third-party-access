import { getJson, postJson } from '@/api/http'

export interface BindingVersionAsset {
  id: number; bindingId: number; versionNo: number; canonicalRequestContractVersionId: number
  canonicalResponseContractVersionId: number; providerContractVersionId: number; endpointId: number; accessChannelId: number | null
  requestMappingVersionId: number; responseMappingVersionId: number; callbackMappingVersionId: number | null
  policyVersionId: number | null; errorMappingVersionId: number | null; idempotencyClass: string
  complianceMetadata: unknown; routingAttributes: unknown; contentChecksum: string
  lifecycleStatus: 'DRAFT' | 'PUBLISHED'; publishedAt: string | null; createdAt: string
}
export interface CreateBindingVersionInput {
  canonicalRequestContractVersionId: number; canonicalResponseContractVersionId: number
  providerContractVersionId: number; endpointId: number; accessChannelId?: number | null; requestMappingVersionId: number
  responseMappingVersionId: number; callbackMappingVersionId: null; policyVersionId: number | null
  errorMappingVersionId: null; complianceMetadata: unknown; routingAttributes: unknown
}
const operator = (import.meta.env.VITE_TPIP_OPERATOR as string | undefined)?.trim() || 'local-ui'
const headers = { 'X-Operator': operator }
export const bindingVersionApi = {
  versions: (bindingId: number, signal?: AbortSignal) => getJson<BindingVersionAsset[]>(`/control/v1/bindings/${bindingId}/versions`, signal),
  create: (bindingId: number, input: CreateBindingVersionInput) => postJson<BindingVersionAsset>(`/control/v1/bindings/${bindingId}/versions`, input, headers),
  publish: (bindingId: number, versionId: number) => postJson<BindingVersionAsset>(`/control/v1/bindings/${bindingId}/versions/${versionId}:publish`, {}, headers),
  preview: (bindingId: number, versionId: number, bundleCode: string, bundleVersion: string, signal?: AbortSignal) => getJson<Record<string, unknown>>(`/control/v1/bindings/${bindingId}/versions/${versionId}/bundle-preview?bundleCode=${encodeURIComponent(bundleCode)}&bundleVersion=${encodeURIComponent(bundleVersion)}`, signal)
}

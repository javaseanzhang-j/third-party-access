import { getJson, postJson } from '@/api/http'

export type ProviderContractVersionStatus = 'DRAFT' | 'PUBLISHED'
export interface ProviderContractVersionAsset {
  id: number; providerContractId: number; versionNo: number; semanticVersion: string
  requestSchema: unknown | null; responseSchema: unknown | null; errorSchema: unknown | null
  callbackSchema: unknown | null; examples: unknown | null; contentChecksum: string
  lifecycleStatus: ProviderContractVersionStatus; publishedAt: string | null; createdAt: string
}
export interface CreateProviderContractVersionInput {
  semanticVersion: string; requestSchema: unknown | null; responseSchema: unknown | null
  errorSchema: unknown | null; callbackSchema: unknown | null; examples: unknown | null
}
const operator = (import.meta.env.VITE_TPIP_OPERATOR as string | undefined)?.trim() || 'local-ui'
const headers = { 'X-Operator': operator }

export const providerContractVersionApi = {
  versions: (contractId: number, signal?: AbortSignal) => getJson<ProviderContractVersionAsset[]>(
    `/control/v1/provider-contracts/${contractId}/versions`, signal),
  create: (contractId: number, input: CreateProviderContractVersionInput) => postJson<ProviderContractVersionAsset>(
    `/control/v1/provider-contracts/${contractId}/versions`, input, headers),
  publish: (contractId: number, versionId: number) => postJson<ProviderContractVersionAsset>(
    `/control/v1/provider-contracts/${contractId}/versions/${versionId}:publish`, {}, headers)
}

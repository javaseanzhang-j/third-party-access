import { getJson, postJson } from '@/api/http'
import type { DataClassification, IdempotencyClass, InvocationMode } from './canonicalAssetApi'

export interface ServiceContractView {
  contractId: number; kind: 'REQUEST' | 'RESPONSE'; contractName: string; versionId: number | null
  semanticVersion: string | null; lifecycleStatus: 'DRAFT' | 'PUBLISHED' | null
}
export interface AdapterTargetView {
  bindingId: number; targetCode: string; targetName: string; status: 'ACTIVE' | 'INACTIVE'
  providerContractId: number; interfaceCode: string; interfaceName: string
  providerId: number; providerName: string
}
export interface ProductAccessService {
  id: number; serviceCode: string; serviceName: string; description: string | null
  invocationMode: InvocationMode; idempotencyClass: IdempotencyClass; dataClassification: DataClassification
  ownerCode: string; status: 'ACTIVE' | 'INACTIVE'; rowVersion: number
  contracts: ServiceContractView[]; targets: AdapterTargetView[]
}
export interface CreateAccessServiceInput {
  serviceCode: string; serviceName: string; description: string | null; invocationMode: InvocationMode
  idempotencyClass: IdempotencyClass; dataClassification: DataClassification; ownerCode: string
  requestSchema: Record<string, unknown>; requestExample: unknown | null
  responseSchema: Record<string, unknown>; responseExample: unknown | null
}
export interface AddAdapterTargetInput { providerContractId: number; targetName: string; ownerCode: string }
export interface ProvisionAdapterTargetInput {
  providerContractId: number; providerContractVersionId: number; accessChannelId: number; endpointId: number
  targetName: string; ownerCode: string
  requestMappings: Array<{ sourcePath: string; targetPath: string; targetType: string; required: boolean }>
  responseMappings: Array<{ sourcePath: string; targetPath: string; targetType: string; required: boolean }>
  authentication: { mode: 'CHANNEL_PARAMETERS' | 'API_KEY_POLICY' | 'HMAC_SHA256_POLICY'; credentialRefId: number | null
    headerName: string | null; prefix: string | null; sourceTemplate: string | null; encoding: 'HEX_LOWER' | 'BASE64' | null }
}
export interface ProvisionedAdapterTarget {
  target: AdapterTargetView; bindingVersionId: number; bindingVersionNo: number; lifecycleStatus: 'PUBLISHED'
  accessChannelId: number; endpointId: number; requestMappingVersionId: number
  responseMappingVersionId: number; policyVersionId: number | null
}

const operator = (import.meta.env.VITE_TPIP_OPERATOR as string | undefined)?.trim() || 'local-ui'
const headers = { 'X-Operator': operator }

export const accessServiceApi = {
  list: (signal?: AbortSignal) => getJson<ProductAccessService[]>('/control/v1/product-model/services', signal),
  get: (id: number, signal?: AbortSignal) => getJson<ProductAccessService>(`/control/v1/product-model/services/${id}`, signal),
  create: (input: CreateAccessServiceInput) => postJson<ProductAccessService>('/control/v1/product-model/services', input, headers),
  addTarget: (id: number, input: AddAdapterTargetInput) => postJson<AdapterTargetView>(
    `/control/v1/product-model/services/${id}/targets`, input, headers),
  provisionTarget: (id: number, input: ProvisionAdapterTargetInput) => postJson<ProvisionedAdapterTarget>(
    `/control/v1/product-model/services/${id}/targets:provision`, input, headers)
}

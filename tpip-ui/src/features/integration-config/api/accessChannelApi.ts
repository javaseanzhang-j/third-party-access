import { getJson, postJson, putJson } from '@/api/http'

export type ChannelStatus = 'ACTIVE' | 'INACTIVE'
export type ParameterScope = 'CHANNEL' | 'INTERFACE'
export type ParameterLocation = 'PATH' | 'QUERY' | 'HEADER' | 'COOKIE' | 'BODY' | 'SIGNATURE'
export type ParameterSource = 'FIXED' | 'SECRET_REF' | 'REQUEST' | 'SYSTEM_TIME' | 'UUID' |
  'EXPRESSION' | 'MAPPING_OUTPUT' | 'POLICY_OUTPUT'
export type ParameterDataType = 'STRING' | 'NUMBER' | 'BOOLEAN' | 'OBJECT' | 'ARRAY'
export type ParameterOverrideMode = 'REPLACE' | 'DISABLE'

export interface AccessChannelAsset {
  id: number; providerId: number; providerProductId: number; channelCode: string; channelName: string; baseUrl: string
  credentialRefId: number | null; description: string | null; status: ChannelStatus; rowVersion: number
  createdAt: string; updatedAt: string
}
export interface AccessParameterAsset {
  id: number; scope: ParameterScope; providerContractId: number | null; parameterCode: string
  parameterName: string; location: ParameterLocation; source: ParameterSource; dataType: ParameterDataType
  valueDocument: string | null; sourceSelector: string | null; secretRefId: number | null
  overrideMode: ParameterOverrideMode; required: boolean; sensitive: boolean; callerOverridable: boolean
  description: string | null; rowVersion: number
}
export interface EffectiveParameterAsset { parameter: AccessParameterAsset; resolvedFrom: ParameterScope }
export interface AccessPolicyVersionAsset {
  id: number; channelId: number; scope: ParameterScope; providerContractId: number | null
  policyCode: string; policyName: string; versionNo: number; normalizedDocument: string | null
  disabledStepIds: string[]; compilerVersion: string; contentChecksum: string
  lifecycleStatus: 'DRAFT' | 'PUBLISHED'; publishedAt: string | null; createdAt: string
}
export interface CreateAccessChannelInput {
  providerId: number; providerProductId: number; channelCode: string; channelName: string; baseUrl: string
  credentialRefId: number | null; description: string | null
}
export interface UpdateAccessChannelInput {
  channelName: string; baseUrl: string; credentialRefId: number | null; description: string | null
  status: ChannelStatus; rowVersion: number
}
export interface UpsertAccessParameterInput {
  scope: ParameterScope; providerContractId: number | null; parameterCode: string; parameterName: string
  location: ParameterLocation; source: ParameterSource; dataType: ParameterDataType; value: unknown
  sourceSelector: string | null; secretRefId: number | null; overrideMode: ParameterOverrideMode
  required: boolean; sensitive: boolean; callerOverridable: boolean; description: string | null
}
export interface CreateAccessPolicyVersionInput {
  scope: ParameterScope; providerContractId: number | null; policyName: string
  document: Record<string, unknown> | null; disabledStepIds: string[]
}
export interface ProviderProductAsset {
  id: number; providerId: number; productCode: string; productName: string
  description: string | null; status: 'ACTIVE' | 'INACTIVE'; createdAt: string
}
export interface CreateProviderProductInput {
  providerId: number; productCode: string; productName: string; description: string | null
}

const operator = (import.meta.env.VITE_TPIP_OPERATOR as string | undefined)?.trim() || 'local-ui'
const headers = { 'X-Operator': operator }

export const accessChannelApi = {
  products: (providerId?: number, signal?: AbortSignal) => getJson<ProviderProductAsset[]>(
    `/control/v1/product-model/provider-products${providerId ? `?providerId=${providerId}` : ''}`, signal),
  createProduct: (input: CreateProviderProductInput) => postJson<ProviderProductAsset>(
    '/control/v1/product-model/provider-products', input, headers),
  productInterfaceIds: (productId: number, signal?: AbortSignal) => getJson<number[]>(
    `/control/v1/product-model/provider-products/${productId}/interfaces`, signal),
  channels: (providerId?: number, signal?: AbortSignal) => getJson<AccessChannelAsset[]>(
    `/control/v1/product-model/channels${providerId ? `?providerId=${providerId}` : ''}`, signal),
  create: (input: CreateAccessChannelInput) => postJson<AccessChannelAsset>(
    '/control/v1/product-model/channels', input, headers),
  update: (channelId: number, input: UpdateAccessChannelInput) => putJson<AccessChannelAsset>(
    `/control/v1/product-model/channels/${channelId}`, input, headers),
  attachInterface: (channelId: number, providerContractId: number) => postJson<void>(
    `/control/v1/product-model/channels/${channelId}/interfaces/${providerContractId}`, {}, headers),
  interfaceIds: (channelId: number, signal?: AbortSignal) => getJson<number[]>(
    `/control/v1/product-model/channels/${channelId}/interfaces`, signal),
  parameters: (channelId: number, signal?: AbortSignal) => getJson<AccessParameterAsset[]>(
    `/control/v1/product-model/channels/${channelId}/parameters`, signal),
  upsertParameter: (channelId: number, input: UpsertAccessParameterInput) => putJson<AccessParameterAsset>(
    `/control/v1/product-model/channels/${channelId}/parameters`, input, headers),
  effective: (channelId: number, providerContractId: number, signal?: AbortSignal) =>
    getJson<EffectiveParameterAsset[]>(`/control/v1/product-model/channels/${channelId}/effective-configuration?providerContractId=${providerContractId}`, signal),
  policyVersions: (channelId: number, scope: ParameterScope, providerContractId?: number | null,
    signal?: AbortSignal) => getJson<AccessPolicyVersionAsset[]>(`/control/v1/product-model/channels/${channelId}/policy-versions?scope=${scope}${providerContractId ? `&providerContractId=${providerContractId}` : ''}`, signal),
  createPolicyVersion: (channelId: number, input: CreateAccessPolicyVersionInput) =>
    postJson<AccessPolicyVersionAsset>(`/control/v1/product-model/channels/${channelId}/policy-versions`, input, headers),
  publishPolicyVersion: (channelId: number, versionId: number) =>
    postJson<AccessPolicyVersionAsset>(`/control/v1/product-model/channels/${channelId}/policy-versions/${versionId}:publish`, {}, headers)
}

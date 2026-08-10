import { getJson, postJson, putJson } from '@/api/http'

export type ChannelStatus = 'ACTIVE' | 'INACTIVE'
export type ParameterScope = 'CHANNEL' | 'INTERFACE'
export type ParameterLocation = 'PATH' | 'QUERY' | 'HEADER' | 'COOKIE' | 'BODY' | 'SIGNATURE'
export type ParameterSource = 'FIXED' | 'SECRET_REF' | 'REQUEST' | 'SYSTEM_TIME' | 'UUID' |
  'EXPRESSION' | 'MAPPING_OUTPUT' | 'POLICY_OUTPUT'
export type ParameterDataType = 'STRING' | 'NUMBER' | 'BOOLEAN' | 'OBJECT' | 'ARRAY'
export type ParameterOverrideMode = 'REPLACE' | 'DISABLE'

export interface AccessChannelAsset {
  id: number; providerId: number; channelCode: string; channelName: string; baseUrl: string
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
export interface CreateAccessChannelInput {
  providerId: number; channelCode: string; channelName: string; baseUrl: string
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

const operator = (import.meta.env.VITE_TPIP_OPERATOR as string | undefined)?.trim() || 'local-ui'
const headers = { 'X-Operator': operator }

export const accessChannelApi = {
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
    getJson<EffectiveParameterAsset[]>(`/control/v1/product-model/channels/${channelId}/effective-configuration?providerContractId=${providerContractId}`, signal)
}

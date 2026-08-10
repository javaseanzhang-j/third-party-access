import { getJson, postJson } from '@/api/http'

export type ProviderType = 'SUPPLIER' | 'CHANNEL' | 'PLATFORM'
export type AssetStatus = 'ACTIVE' | 'INACTIVE'
export type CredentialType = 'API_KEY' | 'BASIC_AUTH' | 'OAUTH2_CLIENT' | 'BEARER_TOKEN' |
  'SIGNING_KEY' | 'MTLS_CERTIFICATE'
export type ProtocolType = 'HTTP' | 'SOAP' | 'GRAPHQL' | 'GRPC'
export type EndpointScheme = 'HTTP' | 'HTTPS'
export type EndpointMethod = 'GET' | 'POST' | 'PUT' | 'PATCH' | 'DELETE'
export type EndpointStatus = 'DRAFT' | 'PUBLISHED' | 'DEPRECATED'

export interface Page<T> { items: T[]; page: number; size: number; totalElements: number; totalPages?: number }
export interface ProviderAsset {
  id: number; providerCode: string; providerName: string; providerType: ProviderType
  description: string | null; ownerCode: string; status: AssetStatus; rowVersion: number
  createdAt: string; updatedAt: string
}
export interface CredentialAsset {
  id: number; providerId: number; credentialCode: string; environmentCode: string
  credentialType: CredentialType; secretUri: string; secretMetadata: Record<string, unknown> | null
  status: AssetStatus; rowVersion: number; createdAt: string; updatedAt: string
}
export interface ProviderContractAsset {
  id: number; providerId: number; contractCode: string; contractName: string
  protocolType: ProtocolType; description: string | null; status: AssetStatus; rowVersion: number
  createdAt: string; updatedAt: string
}
export interface EndpointAsset {
  id: number; providerContractId: number; endpointCode: string; environmentCode: string; revisionNo: number
  protocolScheme: EndpointScheme; baseUrl: string; resourcePath: string; httpMethod: EndpointMethod
  contentType: string | null; charsetName: string; connectTimeoutMs: number; readTimeoutMs: number
  totalTimeoutMs: number; credentialRefId: number | null; lifecycleStatus: EndpointStatus
  contentChecksum: string; publishedAt: string | null; createdAt: string
}
export interface EndpointProbe {
  id: number | null; endpointId: number; outcome: string; reasonCode: string
  latencyMs: number; actorCode: string; createdAt: string
}
export interface CreateProviderInput {
  providerCode: string; providerName: string; providerType: ProviderType
  description: string | null; ownerCode: string
}
export interface CreateCredentialInput {
  providerId: number; credentialCode: string; environmentCode: string
  credentialType: CredentialType; secretUri: string; secretMetadata: Record<string, unknown> | null
}
export interface CreateProviderContractInput {
  providerId: number; contractCode: string; contractName: string
  protocolType: ProtocolType; description: string | null
}
export interface CreateEndpointInput {
  providerContractId: number; endpointCode: string; environmentCode: string
  protocolScheme: EndpointScheme; baseUrl: string; resourcePath: string; httpMethod: EndpointMethod
  contentType: string; charsetName: string; connectTimeoutMs: number; readTimeoutMs: number
  totalTimeoutMs: number; credentialRefId: number | null; networkConfig: Record<string, unknown>; tlsConfig: null
}

const operator = (import.meta.env.VITE_TPIP_OPERATOR as string | undefined)?.trim() || 'local-ui'
const headers = { 'X-Operator': operator }
function query(parameters: Record<string, string | number | undefined>): string {
  const value = new URLSearchParams()
  Object.entries(parameters).forEach(([key, item]) => {
    if (item !== undefined && item !== '') value.set(key, String(item))
  })
  return value.toString()
}

export const integrationAssetApi = {
  providers: (keyword = '', signal?: AbortSignal) => getJson<Page<ProviderAsset>>(
    `/control/v1/providers?${query({ keyword: keyword.trim(), page: 0, size: 200 })}`, signal),
  createProvider: (input: CreateProviderInput) =>
    postJson<ProviderAsset>('/control/v1/providers', input, headers),
  credentials: (providerId?: number, signal?: AbortSignal) => getJson<Page<CredentialAsset>>(
    `/control/v1/credentials?${query({ providerId, page: 0, size: 200 })}`, signal),
  createCredential: (input: CreateCredentialInput) =>
    postJson<CredentialAsset>('/control/v1/credentials', input, headers),
  contracts: (providerId?: number, signal?: AbortSignal) => getJson<Page<ProviderContractAsset>>(
    `/control/v1/provider-contracts?${query({ providerId, page: 0, size: 200 })}`, signal),
  createContract: (input: CreateProviderContractInput) =>
    postJson<ProviderContractAsset>('/control/v1/provider-contracts', input, headers),
  endpoints: (providerContractId?: number, signal?: AbortSignal) => getJson<Page<EndpointAsset>>(
    `/control/v1/endpoints?${query({ providerContractId, latestOnly: 'true', page: 0, size: 200 })}`, signal),
  createEndpoint: (input: CreateEndpointInput) =>
    postJson<EndpointAsset>('/control/v1/endpoints', input, headers),
  publishEndpoint: (endpointId: number) =>
    postJson<EndpointAsset>(`/control/v1/endpoints/${endpointId}:publish`, {}, headers),
  probeEndpoint: (endpointId: number) =>
    postJson<EndpointProbe>(`/control/v1/endpoints/${endpointId}:probe`, {}, headers)
}

import { getJson, postJson } from '@/api/http'
import type { EndpointMethod } from './integrationAssetApi'

export type LifecycleStatus = 'DRAFT' | 'PUBLISHED'
export type CredentialValueSource = 'PUBLIC_VALUE' | 'SECRET_REF'

export interface CredentialProfileItem {
  id: number; fieldCode: string; fieldName: string; valueSource: CredentialValueSource
  displayValue: string | null; secretRefId: number | null; sensitive: boolean; description: string | null
}
export interface CredentialProfile {
  id: number; providerId: number; profileCode: string | { value: string }; profileName: string
  credentialType: string; description: string | null; status: 'ACTIVE' | 'INACTIVE'
}
export interface CredentialProfileView { profile: CredentialProfile; items: CredentialProfileItem[] }
export interface CreateCredentialProfileInput {
  providerId: number; profileCode: string; profileName: string; credentialType: string
  description: string | null
  items: Array<{ fieldCode: string; fieldName: string; valueSource: CredentialValueSource
    publicValue: string | null; secretRefId: number | null; sensitive: boolean; description: string | null }>
}

export interface AuthenticationTemplateVersion {
  id: number; versionNo: number; semanticVersion: string; credentialSchema: Record<string, unknown>
  configurationSchema: Record<string, unknown>; templateDocument: Record<string, unknown>
  lifecycleStatus: LifecycleStatus
}
export interface AuthenticationTemplateView {
  template: { id: number; templateName: string; templateType: string; description: string | null
    status: 'ACTIVE' | 'INACTIVE' }
  versions: AuthenticationTemplateVersion[]
}
export interface ChannelAuthenticationVersion {
  id: number; channelId: number; versionNo: number; authenticationTemplateVersionId: number
  credentialProfileId: number; configuration: Record<string, unknown>; contentChecksum: string
  lifecycleStatus: LifecycleStatus; publishedAt: string | null; createdAt: string
}

export interface InterfaceTransportVersion {
  id: number; providerContractId: number; versionNo: number
  semanticVersion: string | { major: number; minor: number; patch: number }
  resourcePath: string; httpMethod: EndpointMethod; contentType: string | null; charsetName: string
  connectTimeoutMs: number | null; readTimeoutMs: number | null; totalTimeoutMs: number | null
  transportMetadata: string | null; lifecycleStatus: LifecycleStatus; publishedAt: string | null
}
export interface CreateInterfaceTransportInput {
  resourcePath: string; httpMethod: EndpointMethod; contentType: string | null; charsetName: string
  connectTimeoutMs: number | null; readTimeoutMs: number | null; totalTimeoutMs: number | null
  transportMetadata: Record<string, unknown> | null
}

export interface BusinessRequestPreview {
  target: { channelId: number; channelName: string; interfaceId: number; interfaceName: string
    transportVersionId: number; transportVersion: string; finalUrl: string; httpMethod: string
    contentType: string | null; charsetName: string; connectTimeoutMs: number | null
    readTimeoutMs: number | null; totalTimeoutMs: number | null; transportMetadata: Record<string, unknown> }
  authentication: { authenticationVersionId: number; versionNo: number; templateName: string
    templateType: string; templateVersion: string; credentialProfileName: string
    configuration: Record<string, unknown>; credentialFields: Array<{ fieldCode: string; fieldName: string
      sourceType: string; displayValue: string; sensitive: boolean }>
    effects: Array<{ target: string; name: string; description: string; policyType: string }> }
  readiness: string; notice: string
}

const operator = (import.meta.env.VITE_TPIP_OPERATOR as string | undefined)?.trim() || 'local-ui'
const headers = { 'X-Operator': operator }

export const businessIntegrationApi = {
  credentialProfiles: (providerId?: number, signal?: AbortSignal) => getJson<CredentialProfileView[]>(
    `/control/v1/business-integration/credential-profiles${providerId ? `?providerId=${providerId}` : ''}`, signal),
  createCredentialProfile: (input: CreateCredentialProfileInput) => postJson<CredentialProfileView>(
    '/control/v1/business-integration/credential-profiles', input, headers),
  authenticationTemplates: (providerId?: number, signal?: AbortSignal) => getJson<AuthenticationTemplateView[]>(
    `/control/v1/business-integration/authentication-templates${providerId ? `?providerId=${providerId}` : ''}`, signal),
  channelAuthenticationVersions: (channelId: number, signal?: AbortSignal) => getJson<ChannelAuthenticationVersion[]>(
    `/control/v1/business-integration/channels/${channelId}/authentication-versions`, signal),
  createChannelAuthentication: (channelId: number, input: { authenticationTemplateVersionId: number
    credentialProfileId: number; configuration: Record<string, unknown> }) => postJson<ChannelAuthenticationVersion>(
    `/control/v1/business-integration/channels/${channelId}/authentication-versions`, input, headers),
  publishChannelAuthentication: (channelId: number, versionId: number) => postJson<ChannelAuthenticationVersion>(
    `/control/v1/business-integration/channels/${channelId}/authentication-versions/${versionId}:publish`, {}, headers),
  transportVersions: (interfaceId: number, signal?: AbortSignal) => getJson<InterfaceTransportVersion[]>(
    `/control/v1/business-integration/third-party-interfaces/${interfaceId}/transport-versions`, signal),
  createTransportVersion: (interfaceId: number, input: CreateInterfaceTransportInput) =>
    postJson<InterfaceTransportVersion>(
      `/control/v1/business-integration/third-party-interfaces/${interfaceId}/transport-versions`, input, headers),
  publishTransportVersion: (interfaceId: number, versionId: number) => postJson<InterfaceTransportVersion>(
    `/control/v1/business-integration/third-party-interfaces/${interfaceId}/transport-versions/${versionId}:publish`, {}, headers),
  requestPreview: (channelId: number, interfaceId: number, transportVersionId?: number,
    authenticationVersionId?: number, signal?: AbortSignal) => {
    const query = new URLSearchParams()
    if (transportVersionId) query.set('transportVersionId', String(transportVersionId))
    if (authenticationVersionId) query.set('authenticationVersionId', String(authenticationVersionId))
    return getJson<BusinessRequestPreview>(
      `/control/v1/business-integration/channels/${channelId}/interfaces/${interfaceId}/request-preview${query.size ? `?${query}` : ''}`,
      signal)
  }
}

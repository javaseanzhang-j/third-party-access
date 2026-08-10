import { getJson, postJson } from '@/api/http'
import type { Page } from './integrationAssetApi'

export type CatalogStatus = 'ACTIVE' | 'INACTIVE'
export type InvocationMode = 'SYNC' | 'ASYNC' | 'CALLBACK'
export type IdempotencyClass = 'IDEMPOTENT' | 'IDEMPOTENT_WITH_KEY' | 'NON_IDEMPOTENT' | 'UNKNOWN'
export type DataClassification = 'PUBLIC' | 'INTERNAL' | 'CONFIDENTIAL' | 'RESTRICTED'
export type ContractKind = 'REQUEST' | 'RESPONSE' | 'ERROR' | 'EVENT'
export type CompatibilityMode = 'NONE' | 'BACKWARD' | 'FORWARD' | 'FULL'
export type ContractVersionStatus = 'DRAFT' | 'PUBLISHED'

export interface DomainAsset {
  id: number; domainCode: string; domainName: string; description: string | null; ownerCode: string
  status: CatalogStatus; rowVersion: number; createdAt: string; updatedAt: string
}
export interface CapabilityAsset {
  id: number; domainId: number; capabilityCode: string; capabilityName: string; description: string | null
  ownerCode: string; status: CatalogStatus; rowVersion: number; createdAt: string; updatedAt: string
}
export interface OperationAsset {
  id: number; capabilityId: number; operationCode: string; operationName: string; description: string | null
  invocationMode: InvocationMode; idempotencyClass: IdempotencyClass; dataClassification: DataClassification
  ownerCode: string; status: CatalogStatus; rowVersion: number; createdAt: string; updatedAt: string
}
export interface CanonicalContractAsset {
  id: number; operationId: number; contractCode: string; contractName: string; contractKind: ContractKind
  description: string | null; status: CatalogStatus; rowVersion: number; createdAt: string; updatedAt: string
}
export interface CanonicalContractVersionAsset {
  id: number; contractId: number; versionNo: number; semanticVersion: string
  schemaStandard: 'JSON_SCHEMA_2020_12'; schemaDocument: Record<string, unknown>
  exampleDocument: unknown | null; compatibilityMode: CompatibilityMode; contentChecksum: string
  lifecycleStatus: ContractVersionStatus; publishedAt: string | null; createdAt: string
}

export interface CreateDomainInput { domainCode: string; domainName: string; description: string | null; ownerCode: string }
export interface CreateCapabilityInput { domainId: number; capabilityCode: string; capabilityName: string; description: string | null; ownerCode: string }
export interface CreateOperationInput {
  capabilityId: number; operationCode: string; operationName: string; description: string | null
  invocationMode: InvocationMode; idempotencyClass: IdempotencyClass; dataClassification: DataClassification; ownerCode: string
}
export interface CreateCanonicalContractInput {
  operationId: number; contractCode: string; contractName: string; contractKind: ContractKind; description: string | null
}
export interface CreateCanonicalContractVersionInput {
  semanticVersion: string; schemaStandard: 'JSON_SCHEMA_2020_12'; schemaDocument: Record<string, unknown>
  exampleDocument: unknown | null; compatibilityMode: CompatibilityMode
}

const operator = (import.meta.env.VITE_TPIP_OPERATOR as string | undefined)?.trim() || 'local-ui'
const headers = { 'X-Operator': operator }
function query(parameters: Record<string, string | number | undefined>): string {
  const value = new URLSearchParams()
  Object.entries(parameters).forEach(([key, item]) => { if (item !== undefined && item !== '') value.set(key, String(item)) })
  return value.toString()
}

export const canonicalAssetApi = {
  domains: (signal?: AbortSignal) => getJson<Page<DomainAsset>>('/control/v1/domains?page=0&size=200', signal),
  createDomain: (input: CreateDomainInput) => postJson<DomainAsset>('/control/v1/domains', input, headers),
  capabilities: (domainId?: number, signal?: AbortSignal) => getJson<Page<CapabilityAsset>>(
    `/control/v1/capabilities?${query({ domainId, page: 0, size: 200 })}`, signal),
  createCapability: (input: CreateCapabilityInput) => postJson<CapabilityAsset>('/control/v1/capabilities', input, headers),
  operations: (capabilityId?: number, signal?: AbortSignal) => getJson<Page<OperationAsset>>(
    `/control/v1/operations?${query({ capabilityId, page: 0, size: 200 })}`, signal),
  createOperation: (input: CreateOperationInput) => postJson<OperationAsset>('/control/v1/operations', input, headers),
  contracts: (operationId?: number, signal?: AbortSignal) => getJson<Page<CanonicalContractAsset>>(
    `/control/v1/contracts?${query({ operationId, page: 0, size: 200 })}`, signal),
  createContract: (input: CreateCanonicalContractInput) => postJson<CanonicalContractAsset>('/control/v1/contracts', input, headers),
  versions: (contractId: number, signal?: AbortSignal) => getJson<CanonicalContractVersionAsset[]>(
    `/control/v1/contracts/${contractId}/versions`, signal),
  createVersion: (contractId: number, input: CreateCanonicalContractVersionInput) =>
    postJson<CanonicalContractVersionAsset>(`/control/v1/contracts/${contractId}/versions`, input, headers),
  publishVersion: (contractId: number, versionId: number) =>
    postJson<CanonicalContractVersionAsset>(`/control/v1/contracts/${contractId}/versions/${versionId}:publish`, {}, headers)
}

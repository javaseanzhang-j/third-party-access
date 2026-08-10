import { getJson, postJson } from '@/api/http'
import type { Page } from './integrationAssetApi'

export type MappingDirection = 'OUTBOUND_REQUEST' | 'INBOUND_RESPONSE' | 'INBOUND_CALLBACK' | 'OUTBOUND_CALLBACK_RESPONSE'
export type MappingValueSource = 'SELECTOR' | 'CONSTANT' | 'CONTEXT'
export type MappingTargetType = 'STRING' | 'NUMBER' | 'BOOLEAN' | 'OBJECT' | 'ARRAY'
export type MappingArrayStrategy = 'FIRST' | 'ALL' | 'EACH'
export type MappingMissingStrategy = 'IGNORE' | 'DEFAULT' | 'FAIL'
export type MappingErrorStrategy = 'FAIL' | 'IGNORE' | 'USE_DEFAULT'

export interface BindingAsset {
  id: number; bindingCode: string; bindingName: string; operationId: number; providerContractId: number
  ownerCode: string; status: 'ACTIVE' | 'INACTIVE'; rowVersion: number; createdAt: string; updatedAt: string
}
export interface MappingAsset {
  id: number; bindingId: number; mappingCode: string; mappingName: string; direction: MappingDirection
  status: 'ACTIVE' | 'INACTIVE'; rowVersion: number; createdAt: string; updatedAt: string
}
export interface MappingRuleInput {
  ruleCode: string; ruleOrder: number; valueSource: MappingValueSource; sourceSelector: string | null
  targetSelector: string; targetType: MappingTargetType | null; constantValue: unknown | null
  defaultValue: unknown | null; converterCode: string | null; converterConfig: unknown | null
  conditionExpression: string | null; required: boolean; arrayStrategy: MappingArrayStrategy | null
  missingStrategy: MappingMissingStrategy; errorStrategy: MappingErrorStrategy; enabled: boolean
}
export interface MappingVersionAsset {
  id: number; mappingId: number; versionNo: number; selectorProfile: 'JSONPATH_1_0'
  sourceSchemaRef: string; targetSchemaRef: string; mappingOptions: Record<string, unknown> | null
  contentChecksum: string; lifecycleStatus: 'DRAFT' | 'PUBLISHED'; publishedAt: string | null
  createdAt: string; rules: Array<MappingRuleInput & { id: number }>
}
export interface MappingFixtureResult {
  successful: boolean; output: unknown; diagnostics: Array<Record<string, unknown>>; compiledPlanChecksum: string
}
export interface CreateBindingInput { bindingCode: string; bindingName: string; operationId: number; providerContractId: number; ownerCode: string }
export interface CreateMappingInput { bindingId: number; mappingCode: string; mappingName: string; direction: MappingDirection }
export interface CreateMappingVersionInput {
  selectorProfile: 'JSONPATH_1_0'; sourceSchemaRef: string; targetSchemaRef: string
  mappingOptions: Record<string, unknown>; rules: MappingRuleInput[]
}
export interface TestMappingInput { source: unknown; requestId: string; traceId: string; operationCode: string; attributes: Record<string, unknown> }

const operator = (import.meta.env.VITE_TPIP_OPERATOR as string | undefined)?.trim() || 'local-ui'
const headers = { 'X-Operator': operator }
function query(parameters: Record<string, string | number | undefined>): string {
  const value = new URLSearchParams()
  Object.entries(parameters).forEach(([key, item]) => { if (item !== undefined && item !== '') value.set(key, String(item)) })
  return value.toString()
}
export const mappingAssetApi = {
  bindings: (signal?: AbortSignal) => getJson<Page<BindingAsset>>('/control/v1/bindings?page=0&size=200', signal),
  createBinding: (input: CreateBindingInput) => postJson<BindingAsset>('/control/v1/bindings', input, headers),
  mappings: (bindingId?: number, signal?: AbortSignal) => getJson<Page<MappingAsset>>(
    `/control/v1/mappings?${query({ bindingId, page: 0, size: 200 })}`, signal),
  createMapping: (input: CreateMappingInput) => postJson<MappingAsset>('/control/v1/mappings', input, headers),
  versions: (mappingId: number, signal?: AbortSignal) => getJson<MappingVersionAsset[]>(
    `/control/v1/mappings/${mappingId}/versions`, signal),
  createVersion: (mappingId: number, input: CreateMappingVersionInput) => postJson<MappingVersionAsset>(
    `/control/v1/mappings/${mappingId}/versions`, input, headers),
  publishVersion: (mappingId: number, versionId: number) => postJson<MappingVersionAsset>(
    `/control/v1/mappings/${mappingId}/versions/${versionId}:publish`, {}, headers),
  testVersion: (mappingId: number, versionId: number, input: TestMappingInput) => postJson<MappingFixtureResult>(
    `/control/v1/mappings/${mappingId}/versions/${versionId}:test`, input)
}

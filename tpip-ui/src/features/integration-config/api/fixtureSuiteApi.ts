import { getJson, postJson } from '@/api/http'

export type FixtureExecutionMode = 'MAPPING' | 'REMOTE_CALL'
export type FixtureDirection = 'OUTBOUND_REQUEST' | 'INBOUND_RESPONSE'
export type FixtureSuiteStatus = 'ACTIVE' | 'INACTIVE'
export type FixtureSuiteVersionStatus = 'DRAFT' | 'PUBLISHED'

export interface FixtureSuiteAsset {
  id: number; bindingId: number; suiteCode: string; suiteName: string; description: string | null
  status: FixtureSuiteStatus; rowVersion: number; createdAt: string; updatedAt: string
}
export interface FixtureCaseAsset {
  id: number; caseCode: string; caseName: string; caseOrder: number; executionMode: FixtureExecutionMode
  direction: FixtureDirection; source: Record<string, unknown>; expected: Record<string, unknown> | null
  expectedSuccess: boolean; expectedDiagnosticCode: string | null; assertions: unknown[] | null
}
export interface FixtureSuiteVersionAsset {
  id: number; suiteId: number; versionNo: number; contentChecksum: string
  lifecycleStatus: FixtureSuiteVersionStatus; publishedAt: string | null; createdAt: string
  cases: FixtureCaseAsset[]
}
export interface CreateFixtureSuiteInput {
  bindingId: number; suiteCode: string; suiteName: string; description: string | null
}
export interface CreateFixtureCaseInput {
  caseCode: string; caseName: string; caseOrder: number; executionMode: FixtureExecutionMode
  direction: FixtureDirection; source: Record<string, unknown>; expected: Record<string, unknown> | null
  expectedSuccess: boolean; expectedDiagnosticCode: string | null; assertions: unknown[] | null
}

const operator = (import.meta.env.VITE_TPIP_OPERATOR as string | undefined)?.trim() || 'local-ui'
const headers = { 'X-Operator': operator }

export const fixtureSuiteApi = {
  suites: (bindingId: number, signal?: AbortSignal) =>
    getJson<FixtureSuiteAsset[]>(`/control/v1/fixture-suites?bindingId=${bindingId}`, signal),
  createSuite: (input: CreateFixtureSuiteInput) =>
    postJson<FixtureSuiteAsset>('/control/v1/fixture-suites', input, headers),
  versions: (suiteId: number, signal?: AbortSignal) =>
    getJson<FixtureSuiteVersionAsset[]>(`/control/v1/fixture-suites/${suiteId}/versions`, signal),
  createVersion: (suiteId: number, cases: CreateFixtureCaseInput[]) =>
    postJson<FixtureSuiteVersionAsset>(`/control/v1/fixture-suites/${suiteId}/versions`, { cases }, headers),
  publish: (suiteId: number, versionId: number) =>
    postJson<FixtureSuiteVersionAsset>(`/control/v1/fixture-suites/${suiteId}/versions/${versionId}:publish`, {}, headers)
}

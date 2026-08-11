import { getJson, postJson } from '@/api/http'

export type McpToolStatus = 'ACTIVE' | 'INACTIVE'
export type McpToolLifecycle = 'DRAFT' | 'PUBLISHED'
export type McpConfirmationMode = 'NONE' | 'REQUIRED' | 'ALWAYS'

export interface McpToolAsset {
  id: number
  operationId: number
  toolName: string
  displayName: string
  description: string
  ownerCode: string
  status: McpToolStatus
  rowVersion: number
  createdAt: string
  updatedAt: string
}

export interface McpToolVersion {
  id: number
  toolId: number
  versionNo: number
  title: string
  description: string
  fixedScenario: string | null
  inputSchema: string
  outputSchema: string | null
  readOnly: boolean
  destructive: boolean
  idempotent: boolean
  openWorld: boolean
  confirmationMode: McpConfirmationMode
  contentChecksum: string
  lifecycleStatus: McpToolLifecycle
  publishedBy: string | null
  publishedAt: string | null
  createdBy: string
  createdAt: string
}

export interface McpToolSummary {
  tool: McpToolAsset
  serviceCode: string
  serviceName: string
  latestVersion: McpToolVersion | null
  latestPublishedVersion: McpToolVersion | null
}

export interface McpToolDetail {
  summary: McpToolSummary
  versions: McpToolVersion[]
}

export interface McpToolValidationReport { ready: boolean; issues: string[] }
export type McpContractImpactLevel = 'CURRENT' | 'ADDITIVE' | 'BREAKING' | 'UNAVAILABLE'
export interface McpContractSource {
  contractId: number
  contractName: string
  versionId: number
  versionNo: number
  semanticVersion: string
  contentChecksum: string
}
export interface McpSchemaChange {
  level: 'ADDITIVE' | 'BREAKING'
  direction: '请求' | '返回'
  path: string
  message: string
}
export interface McpContractImpact {
  toolId: number
  toolVersionId: number
  toolVersionNo: number
  level: McpContractImpactLevel
  requestContract: McpContractSource | null
  responseContract: McpContractSource | null
  changes: McpSchemaChange[]
  issues: string[]
}

export interface CreateMcpToolInput {
  operationId: number
  toolName: string
  displayName: string
  description: string
  ownerCode: string
}

export interface CreateMcpToolVersionInput {
  title: string
  description: string
  fixedScenario: string | null
  inputSchema: Record<string, unknown>
  outputSchema: Record<string, unknown> | null
  readOnly: boolean
  destructive: boolean
  idempotent: boolean
  openWorld: boolean
  confirmationMode: McpConfirmationMode
}

const operator = (import.meta.env.VITE_TPIP_OPERATOR as string | undefined)?.trim() || 'local-ui'
const headers = { 'X-Operator': operator }

export const mcpToolAssetApi = {
  tools: (signal?: AbortSignal) => getJson<McpToolSummary[]>('/control/v1/mcp-tools', signal),
  tool: (toolId: number, signal?: AbortSignal) => getJson<McpToolDetail>(`/control/v1/mcp-tools/${toolId}`, signal),
  createTool: (input: CreateMcpToolInput) => postJson<McpToolDetail>('/control/v1/mcp-tools', input, headers),
  createVersion: (toolId: number, input: CreateMcpToolVersionInput) =>
    postJson<McpToolVersion>(`/control/v1/mcp-tools/${toolId}/versions`, input, headers),
  validateVersion: (toolId: number, versionId: number) =>
    postJson<McpToolValidationReport>(`/control/v1/mcp-tools/${toolId}/versions/${versionId}:validate`, {}),
  contractImpact: (toolId: number, versionId: number, signal?: AbortSignal) =>
    getJson<McpContractImpact>(`/control/v1/mcp-tools/${toolId}/versions/${versionId}/contract-impact`, signal),
  publishVersion: (toolId: number, versionId: number) =>
    postJson<McpToolVersion>(`/control/v1/mcp-tools/${toolId}/versions/${versionId}:publish`, {}, headers)
}

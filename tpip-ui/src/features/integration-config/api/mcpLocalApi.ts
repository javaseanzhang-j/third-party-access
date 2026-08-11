import { getJson, postJson } from '@/api/http'

export interface McpCatalogStatus {
  lastAttemptAt: string
  lastSuccessfulAt: string
  lastFailedAt: string | null
  trigger: string
  configuredToolCount: number
  exposedToolCount: number
  checksum: string
  message: string
}

export interface McpLocalStatus { applicationCode: string; catalog: McpCatalogStatus }

export interface McpLocalTool {
  toolId: number
  name: string
  title: string
  description: string
  serviceCode: string
  fixedScenario: string | null
  versionNo: number
  inputSchema: Record<string, unknown>
  outputSchema: Record<string, unknown> | null
  readOnly: boolean
  destructive: boolean
  idempotent: boolean
  openWorld: boolean
  confirmationMode: 'NONE' | 'REQUIRED' | 'ALWAYS'
  contentChecksum: string
}

export interface McpLocalCallResult {
  requestId: string
  serviceCode: string
  success: boolean
  resultCode: string
  message: string
  structuredContent: unknown
}

export const mcpLocalApi = {
  status: (signal?: AbortSignal) => getJson<McpLocalStatus>('/mcp-local/v1/status', signal),
  tools: (signal?: AbortSignal) => getJson<McpLocalTool[]>('/mcp-local/v1/tools', signal),
  refresh: () => postJson<McpCatalogStatus>('/mcp-local/v1/catalog:refresh', {}),
  call: (toolName: string, argumentsDocument: Record<string, unknown>, scenario?: string, idempotencyKey?: string) =>
    postJson<McpLocalCallResult>(`/mcp-local/v1/tools/${encodeURIComponent(toolName)}:call`, {
      arguments: argumentsDocument,
      scenario: scenario?.trim() || null,
      idempotencyKey: idempotencyKey?.trim() || null
    })
}

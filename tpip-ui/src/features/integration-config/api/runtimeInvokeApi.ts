export interface RuntimeInvocationRequest {
  meta: { requestId: string; caller: string; tenantId: string | null; idempotencyKey: string | null
    deadline: string | null; attributes: Record<string, string> }
  payload: Record<string, unknown>
}
export interface RuntimeInvocationResult { httpStatus: number; document: Record<string, unknown> }

export const runtimeInvokeApi = {
  invoke: async (operationCode: string, request: RuntimeInvocationRequest): Promise<RuntimeInvocationResult> => {
    const response = await fetch(`/integration/v1/operations/${encodeURIComponent(operationCode)}:invoke`, {
      method: 'POST', headers: { Accept: 'application/json', 'Content-Type': 'application/json' },
      body: JSON.stringify(request)
    })
    const text = await response.text()
    let document: Record<string, unknown>
    try { document = text ? JSON.parse(text) as Record<string, unknown> : {} }
    catch { document = { code: 'TPIP_RUNTIME_INVALID_RESPONSE', message: text } }
    return { httpStatus: response.status, document }
  }
}

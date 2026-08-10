export type RequestEnhancer = (headers: Headers) => void | Promise<void>

let requestEnhancer: RequestEnhancer = () => undefined

export function configureRequestEnhancer(enhancer: RequestEnhancer): void {
  requestEnhancer = enhancer
}

export class ApiError extends Error {
  constructor(
    public readonly status: number,
    message: string,
    public readonly code = 'TPIP_HTTP_ERROR',
    public readonly details: Record<string, unknown> = {}
  ) {
    super(message)
    this.name = 'ApiError'
  }
}

interface ErrorDocument { code?: string; message?: string; details?: Record<string, unknown> }

async function requestJson<T>(path: string, method: 'GET' | 'POST' | 'PUT', body?: unknown,
  additionalHeaders?: HeadersInit, signal?: AbortSignal): Promise<T> {
  const headers = new Headers({ Accept: 'application/json', ...additionalHeaders })
  if (body !== undefined) headers.set('Content-Type', 'application/json')
  await requestEnhancer(headers)
  const response = await fetch(path, {
    method, headers, signal,
    ...(body === undefined ? {} : { body: JSON.stringify(body) })
  })
  if (!response.ok) {
    const text = await response.text()
    let document: ErrorDocument = {}
    try { document = text ? JSON.parse(text) as ErrorDocument : {} } catch { /* plain-text upstream */ }
    throw new ApiError(response.status, document.message || text || `请求失败（HTTP ${response.status}）`,
      document.code, document.details)
  }
  if (response.status === 204) return undefined as T
  return response.json() as Promise<T>
}

export function getJson<T>(path: string, signal?: AbortSignal): Promise<T> {
  return requestJson<T>(path, 'GET', undefined, undefined, signal)
}

export function postJson<T>(path: string, body: unknown, headers?: HeadersInit,
  signal?: AbortSignal): Promise<T> {
  return requestJson<T>(path, 'POST', body, headers, signal)
}

export function putJson<T>(path: string, body: unknown, headers?: HeadersInit,
  signal?: AbortSignal): Promise<T> {
  return requestJson<T>(path, 'PUT', body, headers, signal)
}

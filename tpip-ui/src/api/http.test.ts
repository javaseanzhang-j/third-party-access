import { ApiError, configureRequestEnhancer, getJson, postJson, putJson } from './http'

describe('http client', () => {
  afterEach(() => { vi.unstubAllGlobals(); configureRequestEnhancer(() => undefined) })

  it('keeps authentication enhancement optional and parses json', async () => {
    configureRequestEnhancer(headers => headers.set('X-Test', 'local'))
    const fetchMock = vi.fn(async (_path: string, init: RequestInit) => {
      expect(new Headers(init.headers).get('X-Test')).toBe('local')
      return new Response(JSON.stringify({ ok: true }), { status: 200, headers: { 'Content-Type': 'application/json' } })
    })
    vi.stubGlobal('fetch', fetchMock)
    await expect(getJson<{ ok: boolean }>('/control/test')).resolves.toEqual({ ok: true })
  })

  it('returns a typed error without exposing invented credentials', async () => {
    vi.stubGlobal('fetch', vi.fn(async () => new Response('not found', { status: 404 })))
    await expect(getJson('/missing')).rejects.toEqual(new ApiError(404, 'not found'))
  })

  it('posts json and parses the platform error contract', async () => {
    const fetchMock = vi.fn(async (_path: string, init: RequestInit) => {
      expect(init.method).toBe('POST')
      expect(new Headers(init.headers).get('X-Operator')).toBe('local-ui')
      expect(JSON.parse(String(init.body))).toEqual({ rowVersion: 3 })
      return new Response(JSON.stringify({
        code: 'TPIP_GLOBAL_IMPACT_JOB_COMMAND_CONFLICT',
        message: 'changed concurrently', details: { refreshRequired: true }
      }), { status: 409, headers: { 'Content-Type': 'application/json' } })
    })
    vi.stubGlobal('fetch', fetchMock)

    await expect(postJson('/command', { rowVersion: 3 }, { 'X-Operator': 'local-ui' }))
      .rejects.toMatchObject({ status: 409, code: 'TPIP_GLOBAL_IMPACT_JOB_COMMAND_CONFLICT',
        details: { refreshRequired: true } })
  })

  it('supports PUT commands and empty successful responses', async () => {
    const fetchMock = vi.fn(async () => new Response(null, { status: 204 }))
    vi.stubGlobal('fetch', fetchMock)
    await expect(putJson<void>('/resource/1', { enabled: true })).resolves.toBeUndefined()
    expect(fetchMock).toHaveBeenCalledWith('/resource/1', expect.objectContaining({ method: 'PUT' }))
  })
})

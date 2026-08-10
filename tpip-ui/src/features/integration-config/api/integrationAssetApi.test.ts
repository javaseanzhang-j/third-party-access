import { integrationAssetApi } from './integrationAssetApi'

describe('integration asset api', () => {
  afterEach(() => vi.unstubAllGlobals())

  it('loads provider assets with bounded pagination', async () => {
    const response = { items: [], page: 0, size: 200, totalElements: 0, totalPages: 0 }
    const fetchMock = vi.fn(async (_input: RequestInfo | URL, _init?: RequestInit) => new Response(JSON.stringify(response), {
      status: 200, headers: { 'Content-Type': 'application/json' }
    }))
    vi.stubGlobal('fetch', fetchMock)
    await expect(integrationAssetApi.providers('vendor')).resolves.toEqual(response)
    expect(fetchMock).toHaveBeenCalledWith(
      '/control/v1/providers?keyword=vendor&page=0&size=200', expect.objectContaining({ method: 'GET' }))
  })

  it('creates a credential reference without carrying a secret value', async () => {
    const input = { providerId: 7, credentialCode: 'vendor.api-key', environmentCode: 'test' as const,
      credentialType: 'API_KEY' as const, secretUri: 'env://TPIP_SECRET_VENDOR_API_KEY',
      secretMetadata: { purpose: 'lookup' } }
    const fetchMock = vi.fn(async (_input: RequestInfo | URL, _init?: RequestInit) => new Response(JSON.stringify({ id: 9, ...input }), {
      status: 201, headers: { 'Content-Type': 'application/json' }
    }))
    vi.stubGlobal('fetch', fetchMock)
    await integrationAssetApi.createCredential(input)
    const [path, init] = fetchMock.mock.calls[0] as unknown as [string, RequestInit]
    expect(path).toBe('/control/v1/credentials')
    expect(new Headers(init.headers).get('X-Operator')).toBe('local-ui')
    expect(init.body).toBe(JSON.stringify(input))
    expect(init.body).not.toContain('secretValue')
  })

  it('publishes and probes endpoint revisions with operator evidence', async () => {
    const fetchMock = vi.fn(async (_input: RequestInfo | URL, _init?: RequestInit) => new Response('{}', {
      status: 200, headers: { 'Content-Type': 'application/json' }
    }))
    vi.stubGlobal('fetch', fetchMock)
    await integrationAssetApi.publishEndpoint(12)
    await integrationAssetApi.probeEndpoint(12)
    expect(fetchMock.mock.calls.map(call => call[0])).toEqual([
      '/control/v1/endpoints/12:publish', '/control/v1/endpoints/12:probe'
    ])
    for (const call of fetchMock.mock.calls) {
      expect(new Headers(call[1]?.headers).get('X-Operator')).toBe('local-ui')
    }
  })
})

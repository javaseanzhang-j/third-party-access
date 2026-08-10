import { canonicalAssetApi } from './canonicalAssetApi'

describe('canonical asset api', () => {
  afterEach(() => vi.unstubAllGlobals())

  it('loads hierarchy assets with bounded pagination and parent filters', async () => {
    const response = { items: [], page: 0, size: 200, totalElements: 0 }
    const fetchMock = vi.fn(async (_input: RequestInfo | URL, _init?: RequestInit) => new Response(JSON.stringify(response), {
      status: 200, headers: { 'Content-Type': 'application/json' }
    }))
    vi.stubGlobal('fetch', fetchMock)
    await canonicalAssetApi.domains()
    await canonicalAssetApi.capabilities(7)
    await canonicalAssetApi.operations(9)
    await canonicalAssetApi.contracts(11)
    expect(fetchMock.mock.calls.map(call => call[0])).toEqual([
      '/control/v1/domains?page=0&size=200',
      '/control/v1/capabilities?domainId=7&page=0&size=200',
      '/control/v1/operations?capabilityId=9&page=0&size=200',
      '/control/v1/contracts?operationId=11&page=0&size=200'
    ])
  })

  it('creates and publishes a canonical schema version with operator evidence', async () => {
    const fetchMock = vi.fn(async (_input: RequestInfo | URL, _init?: RequestInit) => new Response('{}', {
      status: 200, headers: { 'Content-Type': 'application/json' }
    }))
    vi.stubGlobal('fetch', fetchMock)
    const input = { semanticVersion: '1.0.0', schemaStandard: 'JSON_SCHEMA_2020_12' as const,
      schemaDocument: { type: 'object' }, exampleDocument: {}, compatibilityMode: 'BACKWARD' as const }
    await canonicalAssetApi.createVersion(3, input)
    await canonicalAssetApi.publishVersion(3, 5)
    const [, createInit] = fetchMock.mock.calls[0] as unknown as [string, RequestInit]
    expect(JSON.parse(String(createInit.body))).toEqual(input)
    expect(new Headers(createInit.headers).get('X-Operator')).toBe('local-ui')
    expect(fetchMock.mock.calls[1]?.[0]).toBe('/control/v1/contracts/3/versions/5:publish')
  })
})

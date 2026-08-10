import { mappingAssetApi } from './mappingAssetApi'
import { providerContractVersionApi } from './providerContractVersionApi'

describe('mapping configuration api', () => {
  afterEach(() => vi.unstubAllGlobals())
  it('creates and publishes provider contract versions', async () => {
    const fetchMock = vi.fn(async (_input: RequestInfo | URL, _init?: RequestInit) => new Response('{}', { status: 200, headers: { 'Content-Type': 'application/json' } }))
    vi.stubGlobal('fetch', fetchMock)
    await providerContractVersionApi.create(4, { requestSchema: { type: 'object' }, responseSchema: null, errorSchema: null, callbackSchema: null, examples: {} })
    await providerContractVersionApi.publish(4, 6)
    expect(fetchMock.mock.calls.map(call => call[0])).toEqual(['/control/v1/provider-contracts/4/versions', '/control/v1/provider-contracts/4/versions/6:publish'])
    expect(new Headers(fetchMock.mock.calls[0]?.[1]?.headers).get('X-Operator')).toBe('local-ui')
  })
  it('tests a mapping without adding operator authentication semantics', async () => {
    const fetchMock = vi.fn(async (_input: RequestInfo | URL, _init?: RequestInit) => new Response(JSON.stringify({ successful: true, output: {}, diagnostics: [], compiledPlanChecksum: 'abc' }), { status: 200, headers: { 'Content-Type': 'application/json' } }))
    vi.stubGlobal('fetch', fetchMock)
    await mappingAssetApi.testVersion(3, 7, { source: { customerId: 'C1' }, requestId: 'r1', traceId: 't1', operationCode: 'customer.lookup', attributes: {} })
    const [path, init] = fetchMock.mock.calls[0] as unknown as [string, RequestInit]
    expect(path).toBe('/control/v1/mappings/3/versions/7:test')
    expect(new Headers(init.headers).get('X-Operator')).toBeNull()
  })
})

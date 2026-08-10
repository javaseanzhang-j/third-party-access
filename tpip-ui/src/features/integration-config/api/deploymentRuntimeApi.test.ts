import { deploymentApi } from './deploymentApi'
import { runtimeInvokeApi } from './runtimeInvokeApi'

describe('deployment and runtime api', () => {
  afterEach(() => vi.unstubAllGlobals())

  it('preheats, activates and rolls back with row versions', async () => {
    const fetchMock = vi.fn(async (_input: RequestInfo | URL, _init?: RequestInit) => new Response('{}', { status: 200, headers: { 'Content-Type': 'application/json' } }))
    vi.stubGlobal('fetch', fetchMock)
    await deploymentApi.preheat(7, 1); await deploymentApi.activate(7, 3, 10); await deploymentApi.rollback(7, 4, 'rollback.customer.v1', 'provider regression')
    expect(JSON.parse(String((fetchMock.mock.calls[0]?.[1] as RequestInit).body))).toEqual({ rowVersion: 1 })
    expect(JSON.parse(String((fetchMock.mock.calls[1]?.[1] as RequestInit).body))).toEqual({ rowVersion: 3, initialTraffic: 10 })
    expect(fetchMock.mock.calls[2]?.[0]).toBe('/control/v1/deployments/7:rollback')
  })

  it('returns structured runtime errors without hiding the HTTP status', async () => {
    const fetchMock = vi.fn(async () => new Response(JSON.stringify({ code: 'TPIP_RUNTIME_BUNDLE_UNAVAILABLE', message: 'no route' }), { status: 503, headers: { 'Content-Type': 'application/json' } }))
    vi.stubGlobal('fetch', fetchMock)
    const result = await runtimeInvokeApi.invoke('customer.lookup', { meta: { requestId: 'req-1', caller: 'local-ui', tenantId: null, idempotencyKey: null, deadline: null, attributes: {} }, payload: { customerId: 'C1001' } },
      { appKey: 'tpip_test', appSecret: 'local-test-secret', scenario: null })
    expect(result.httpStatus).toBe(503)
    expect(result.document.code).toBe('TPIP_RUNTIME_BUNDLE_UNAVAILABLE')
  })
})

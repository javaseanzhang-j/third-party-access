import { fixtureSuiteApi } from './fixtureSuiteApi'

describe('fixture suite api', () => {
  afterEach(() => vi.unstubAllGlobals())

  it('creates a version with typed assertions and local operator', async () => {
    const fetchMock = vi.fn(async (_input: RequestInfo | URL, _init?: RequestInit) => new Response('{}', { status: 200, headers: { 'Content-Type': 'application/json' } }))
    vi.stubGlobal('fetch', fetchMock)
    const cases = [{ caseCode: 'request.success', caseName: '请求映射成功', caseOrder: 0,
      executionMode: 'MAPPING' as const, direction: 'OUTBOUND_REQUEST' as const,
      source: { customerId: 'C1001' }, expected: { customer_id: 'C1001' }, expectedSuccess: true,
      expectedDiagnosticCode: null, assertions: [{ code: 'mapping-success', type: 'SUCCESS', expected: true }] }]
    await fixtureSuiteApi.createVersion(7, cases)
    const [path, init] = fetchMock.mock.calls[0] as unknown as [string, RequestInit]
    expect(path).toBe('/control/v1/fixture-suites/7/versions')
    expect(JSON.parse(String(init.body))).toEqual({ cases })
    expect(new Headers(init.headers).get('X-Operator')).toBe('local-ui')
  })

  it('publishes an immutable fixture version', async () => {
    const fetchMock = vi.fn(async (_input: RequestInfo | URL, _init?: RequestInit) => new Response('{}', { status: 200, headers: { 'Content-Type': 'application/json' } }))
    vi.stubGlobal('fetch', fetchMock)
    await fixtureSuiteApi.publish(7, 11)
    expect(fetchMock.mock.calls[0]?.[0]).toBe('/control/v1/fixture-suites/7/versions/11:publish')
  })
})

import { bindingVersionApi } from './bindingVersionApi'
import { policyAssetApi } from './policyAssetApi'

describe('policy and binding version api', () => {
  afterEach(() => vi.unstubAllGlobals())
  it('compiles a policy document through version creation and fetches its plan', async () => {
    const fetchMock = vi.fn(async (_input: RequestInfo | URL, _init?: RequestInit) => new Response('{}', { status: 200, headers: { 'Content-Type': 'application/json' } }))
    vi.stubGlobal('fetch', fetchMock)
    const document = { apiVersion: 'tpip.policy/v1alpha1', kind: 'PolicyChain', stages: {} }
    await policyAssetApi.createVersion(3, document); await policyAssetApi.plan(3, 8)
    const [path, init] = fetchMock.mock.calls[0] as unknown as [string, RequestInit]
    expect(path).toBe('/control/v1/policies/3/versions'); expect(init.body).toBe(JSON.stringify({ document }))
    expect(fetchMock.mock.calls[1]?.[0]).toBe('/control/v1/policies/3/versions/8/plan')
  })
  it('creates an immutable binding dependency closure', async () => {
    const fetchMock = vi.fn(async (_input: RequestInfo | URL, _init?: RequestInit) => new Response('{}', { status: 200, headers: { 'Content-Type': 'application/json' } }))
    vi.stubGlobal('fetch', fetchMock)
    const input = { canonicalRequestContractVersionId: 1, canonicalResponseContractVersionId: 2, providerContractVersionId: 3, endpointId: 4, requestMappingVersionId: 5, responseMappingVersionId: 6, callbackMappingVersionId: null, policyVersionId: 7, errorMappingVersionId: null, complianceMetadata: {}, routingAttributes: {} }
    await bindingVersionApi.create(9, input)
    const [, init] = fetchMock.mock.calls[0] as unknown as [string, RequestInit]
    expect(JSON.parse(String(init.body))).toEqual(input); expect(new Headers(init.headers).get('X-Operator')).toBe('local-ui')
  })
})

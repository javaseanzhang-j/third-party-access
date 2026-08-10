import { governancePolicyAssetApi } from './governancePolicyAssetApi'

describe('governance policy asset api', () => {
  afterEach(() => vi.unstubAllGlobals())

  it('sends stable server-side policy filters and paging', async () => {
    const response = { items: [], page: 1, size: 20, totalElements: 0, totalPages: 0 }
    const fetchMock = vi.fn(async () => new Response(JSON.stringify(response), {
      status: 200, headers: { 'Content-Type': 'application/json' }
    }))
    vi.stubGlobal('fetch', fetchMock)

    await governancePolicyAssetApi.policies({ scope: 'GLOBAL', status: 'ACTIVE', keyword: ' core ', page: 1 })

    expect(fetchMock).toHaveBeenCalledWith(expect.stringMatching(
      /policy-asset-views\?scope=GLOBAL&status=ACTIVE&keyword=core&page=1&size=20$/), expect.any(Object))
  })

  it('loads a dedicated immutable version projection', async () => {
    const response = { policy: { policyId: 7 }, version: { versionId: 8 }, recentImpactJobs: [] }
    const fetchMock = vi.fn(async () => new Response(JSON.stringify(response), {
      status: 200, headers: { 'Content-Type': 'application/json' }
    }))
    vi.stubGlobal('fetch', fetchMock)

    await governancePolicyAssetApi.version(7, 8)

    expect(fetchMock).toHaveBeenCalledWith(expect.stringMatching(
      /policy-asset-views\/7\/versions\/8\?recentJobLimit=20$/), expect.any(Object))
  })
})

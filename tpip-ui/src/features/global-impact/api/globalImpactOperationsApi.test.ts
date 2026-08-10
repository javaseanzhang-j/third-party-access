import { globalImpactOperationsApi } from './globalImpactOperationsApi'

describe('global impact operations api', () => {
  afterEach(() => vi.unstubAllGlobals())

  it('reads the bounded stable operations view', async () => {
    const response = { generatedAt: '2026-08-09T08:00:00Z', stalledCount: 0, criticalCount: 0,
      warningCount: 0, recoveryRecommendations: [], items: [], limitReached: false }
    const fetchMock = vi.fn(async () => new Response(JSON.stringify(response), {
      status: 200, headers: { 'Content-Type': 'application/json' }
    }))
    vi.stubGlobal('fetch', fetchMock)

    await expect(globalImpactOperationsApi.overview(100)).resolves.toEqual(response)
    expect(fetchMock).toHaveBeenCalledWith(expect.stringMatching(
      /global-governance-policy-impact-operations-view\?limit=100$/), expect.any(Object))
  })
})

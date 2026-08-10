import { getDriftGroups, getGovernanceMetrics } from './driftGovernanceMetricsApi'

describe('drift governance metrics api', () => {
  afterEach(() => vi.unstubAllGlobals())

  it('uses policy SLA unless an explicit analysis override is supplied', async () => {
    const fetchMock = vi.fn(async () => new Response(JSON.stringify({ workspaceId: 23 }), {
      status: 200, headers: { 'Content-Type': 'application/json' }
    }))
    vi.stubGlobal('fetch', fetchMock)
    await getGovernanceMetrics({ workspaceId: 23, windowDays: 30 })
    expect(fetchMock).toHaveBeenCalledWith(
      '/control/v1/verification-drift-workbench/governance-metrics?workspaceId=23&windowDays=30',
      expect.any(Object)
    )
  })

  it('aligns group overdue calculation with the effective SLA', async () => {
    const fetchMock = vi.fn(async () => new Response('[]', {
      status: 200, headers: { 'Content-Type': 'application/json' }
    }))
    vi.stubGlobal('fetch', fetchMock)
    await getDriftGroups({ workspaceId: 23, scope: 'ALL', overdueAfterHours: 48, limit: 20 })
    expect(fetchMock).toHaveBeenCalledWith(expect.stringMatching(
      /groups\?workspaceId=23&scope=ALL&overdueOnly=false&overdueAfterHours=48&limit=20$/
    ), expect.any(Object))
  })
})

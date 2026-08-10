import { globalImpactSnapshotApi } from './globalImpactSnapshotApi'

describe('global impact snapshot api', () => {
  afterEach(() => vi.unstubAllGlobals())

  it('reads structured workspace evidence from the stable snapshot view endpoint', async () => {
    const response = { snapshotId: 'snapshot-1', items: [], page: 1, size: 10,
      totalElements: 0, totalPages: 0, hasNext: false }
    const fetchMock = vi.fn(async () => new Response(JSON.stringify(response), {
      status: 200, headers: { 'Content-Type': 'application/json' }
    }))
    vi.stubGlobal('fetch', fetchMock)

    await expect(globalImpactSnapshotApi.workspaces('snapshot-1', 1, 10)).resolves.toEqual(response)
    expect(fetchMock).toHaveBeenCalledWith(expect.stringMatching(
      /global-governance-policy-impact-snapshot-views\/snapshot-1\/workspace-snapshots\?page=1&size=10$/),
    expect.any(Object))
  })
})

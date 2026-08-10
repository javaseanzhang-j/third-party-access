import { globalImpactApi } from './globalImpactApi'

describe('global impact api', () => {
  afterEach(() => vi.unstubAllGlobals())

  it('reads the full-job impact summary from the dedicated view endpoint', async () => {
    const response = {
      jobId: 'job-1', workspaceCount: 1,
      risks: { low: 1, medium: 0, high: 0, critical: 0 },
      statuses: { pending: 0, running: 0, succeeded: 1, failed: 0 },
      impactTotals: { changedReports: 2, newlyOverdueReports: 1, addedReminderCandidates: 0 }
    }
    const fetchMock = vi.fn(async () => new Response(JSON.stringify(response), {
      status: 200,
      headers: { 'Content-Type': 'application/json' }
    }))
    vi.stubGlobal('fetch', fetchMock)

    await expect(globalImpactApi.impactSummary('job-1')).resolves.toEqual(response)
    expect(fetchMock).toHaveBeenCalledWith(
      expect.stringMatching(/\/job-1\/impact-summary$/), expect.any(Object)
    )
  })

  it('sends server-side workspace diagnosis filters with paging', async () => {
    const response = { jobId: 'job-1', items: [], page: 0, size: 10, totalElements: 0, totalPages: 0, hasNext: false }
    const fetchMock = vi.fn(async () => new Response(JSON.stringify(response), {
      status: 200, headers: { 'Content-Type': 'application/json' }
    }))
    vi.stubGlobal('fetch', fetchMock)

    await globalImpactApi.impacts('job-1', {
      status: 'FAILED', riskLevel: 'CRITICAL', keyword: ' payment ', page: 0, size: 10
    })

    expect(fetchMock).toHaveBeenCalledWith(expect.stringMatching(
      /workspace-impacts\?status=FAILED&riskLevel=CRITICAL&keyword=\+payment\+&page=0&size=10$/),
    expect.any(Object))
  })
})

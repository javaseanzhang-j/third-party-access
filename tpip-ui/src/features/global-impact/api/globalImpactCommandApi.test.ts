import { globalImpactCommandApi } from './globalImpactCommandApi'

describe('global impact command api', () => {
  afterEach(() => vi.unstubAllGlobals())

  it('sends audit actor, row version and reason when cancelling', async () => {
    const fetchMock = vi.fn(async (_path: string, init: RequestInit) => new Response(JSON.stringify({
      jobId: 'job-1', status: 'CANCELLED', rowVersion: 4
    }), { status: 200, headers: { 'Content-Type': 'application/json' } }))
    vi.stubGlobal('fetch', fetchMock)

    await globalImpactCommandApi.cancel('job-1', 3, '不再需要本次计算')

    expect(fetchMock).toHaveBeenCalledWith(expect.stringMatching(/\/job-1:cancel$/),
      expect.objectContaining({ method: 'POST', body: JSON.stringify({
        rowVersion: 3, reason: '不再需要本次计算'
      }) }))
    const init = fetchMock.mock.calls[0]?.[1]
    expect(new Headers(init?.headers).get('X-Operator')).toBe('local-ui')
  })
})

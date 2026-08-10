import { workspaceReleaseApi } from './workspaceReleaseApi'

describe('workspace release api', () => {
  afterEach(() => vi.unstubAllGlobals())

  it('submits review with optimistic row version', async () => {
    const fetchMock = vi.fn(async (_input: RequestInfo | URL, _init?: RequestInit) => new Response('{}', { status: 200, headers: { 'Content-Type': 'application/json' } }))
    vi.stubGlobal('fetch', fetchMock)
    await workspaceReleaseApi.submitReview(4, 3)
    const [path, init] = fetchMock.mock.calls[0] as unknown as [string, RequestInit]
    expect(path).toBe('/control/v1/workspaces/4:submit-review')
    expect(JSON.parse(String(init.body))).toEqual({ rowVersion: 3 })
  })

  it('uses the explicit approver as audit operator', async () => {
    const fetchMock = vi.fn(async (_input: RequestInfo | URL, _init?: RequestInit) => new Response('{}', { status: 200, headers: { 'Content-Type': 'application/json' } }))
    vi.stubGlobal('fetch', fetchMock)
    await workspaceReleaseApi.decide(4, { approvalStage: 'SECURITY', decision: 'APPROVED',
      decisionComment: 'reviewed', evidenceSnapshot: { verificationRunId: 12 }, rowVersion: 4,
      approverCode: 'security-reviewer' })
    const [, init] = fetchMock.mock.calls[0] as unknown as [string, RequestInit]
    expect(new Headers(init.headers).get('X-Operator')).toBe('security-reviewer')
    expect(JSON.parse(String(init.body))).not.toHaveProperty('approverCode')
  })

  it('compiles and publishes a bundle', async () => {
    const fetchMock = vi.fn(async (_input: RequestInfo | URL, _init?: RequestInit) => new Response('{}', { status: 200, headers: { 'Content-Type': 'application/json' } }))
    vi.stubGlobal('fetch', fetchMock)
    await workspaceReleaseApi.compile(4, 'customer.lookup.local', 5)
    await workspaceReleaseApi.publish(9)
    expect(fetchMock.mock.calls[0]?.[0]).toBe('/control/v1/workspaces/4/bundles')
    expect(fetchMock.mock.calls[1]?.[0]).toBe('/control/v1/bundles/9:publish')
  })
})

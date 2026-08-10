import { workspaceVerificationApi } from './workspaceVerificationApi'

describe('workspace verification api', () => {
  afterEach(() => vi.unstubAllGlobals())

  it('adds one published binding version to a workspace', async () => {
    const fetchMock = vi.fn(async (_input: RequestInfo | URL, _init?: RequestInit) => new Response('{}', { status: 200, headers: { 'Content-Type': 'application/json' } }))
    vi.stubGlobal('fetch', fetchMock)
    await workspaceVerificationApi.addBindingVersion(3, 5, 8)
    const [path, init] = fetchMock.mock.calls[0] as unknown as [string, RequestInit]
    expect(path).toBe('/control/v1/workspaces/3/assets/binding-version')
    expect(JSON.parse(String(init.body))).toEqual({ bindingId: 5, bindingVersionId: 8, changeType: 'ADD', dependencyMetadata: {} })
    expect(new Headers(init.headers).get('X-Operator')).toBe('local-ui')
  })

  it('runs and retries server verification with row version', async () => {
    const fetchMock = vi.fn(async (_input: RequestInfo | URL, _init?: RequestInit) => new Response('{}', { status: 200, headers: { 'Content-Type': 'application/json' } }))
    vi.stubGlobal('fetch', fetchMock)
    await workspaceVerificationApi.verify(3, 11, 2)
    await workspaceVerificationApi.retry(19, 2)
    expect(JSON.parse(String((fetchMock.mock.calls[0]?.[1] as RequestInit).body))).toEqual({ fixtureSuiteVersionId: 11, rowVersion: 2 })
    expect(fetchMock.mock.calls[1]?.[0]).toBe('/control/v1/verification-jobs/19:retry')
  })
})

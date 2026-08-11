import { mcpToolAssetApi } from './mcpToolAssetApi'

describe('mcp tool asset api', () => {
  afterEach(() => vi.unstubAllGlobals())

  it('uses the immutable version validation and publication commands', async () => {
    const calls: Array<[RequestInfo | URL, RequestInit | undefined]> = []
    const fetchMock = vi.fn(async (input: RequestInfo | URL, init?: RequestInit) => {
      calls.push([input, init])
      return new Response(JSON.stringify({ ready: true, issues: [] }), {
        status: 200, headers: { 'Content-Type': 'application/json' }
      })
    })
    vi.stubGlobal('fetch', fetchMock)

    await mcpToolAssetApi.validateVersion(3, 8)
    await mcpToolAssetApi.publishVersion(3, 8)

    expect(calls[0]?.[0]).toBe('/control/v1/mcp-tools/3/versions/8:validate')
    expect(calls[1]?.[0]).toBe('/control/v1/mcp-tools/3/versions/8:publish')
    expect(new Headers(calls[1]?.[1]?.headers).get('X-Operator')).toBeTruthy()
  })
})

import { mcpLocalApi } from './mcpLocalApi'

describe('local MCP operations api', () => {
  afterEach(() => vi.unstubAllGlobals())

  it('refreshes the runtime catalog and calls a selected tool', async () => {
    const calls: Array<[RequestInfo | URL, RequestInit | undefined]> = []
    vi.stubGlobal('fetch', vi.fn(async (input: RequestInfo | URL, init?: RequestInit) => {
      calls.push([input, init])
      return new Response(JSON.stringify({ success: true }), {
        status: 200, headers: { 'Content-Type': 'application/json' }
      })
    }))

    await mcpLocalApi.refresh()
    await mcpLocalApi.call('sms_send', { mobile: '13800000000' }, 'verification-code')

    expect(calls[0]?.[0]).toBe('/mcp-local/v1/catalog:refresh')
    expect(calls[1]?.[0]).toBe('/mcp-local/v1/tools/sms_send:call')
    expect(JSON.parse(calls[1]?.[1]?.body as string)).toMatchObject({
      arguments: { mobile: '13800000000' }, scenario: 'verification-code'
    })
  })
})

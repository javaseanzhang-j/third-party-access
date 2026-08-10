import { systemApi } from './systemApi'

describe('system api', () => {
  afterEach(() => vi.unstubAllGlobals())

  it('uses the loopback-proxied actuator health endpoint', async () => {
    const fetchMock = vi.fn(async () => new Response('{"status":"UP"}', {
      status: 200,
      headers: { 'Content-Type': 'application/json' }
    }))
    vi.stubGlobal('fetch', fetchMock)

    await expect(systemApi.health()).resolves.toEqual({ status: 'UP' })
    expect(fetchMock).toHaveBeenCalledWith('/actuator/health', expect.any(Object))
  })
})

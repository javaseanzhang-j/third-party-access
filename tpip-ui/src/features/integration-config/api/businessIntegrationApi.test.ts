import { businessIntegrationApi } from './businessIntegrationApi'

describe('business integration api', () => {
  afterEach(() => vi.unstubAllGlobals())

  it('creates and publishes an interface transport version', async () => {
    const fetchMock = vi.fn(async () => new Response(JSON.stringify({ id: 9, lifecycleStatus: 'DRAFT' }), {
      status: 201, headers: { 'Content-Type': 'application/json' }
    }))
    vi.stubGlobal('fetch', fetchMock)
    await businessIntegrationApi.createTransportVersion(7, { resourcePath: '/v1/messages', httpMethod: 'POST',
      contentType: 'application/json', charsetName: 'UTF-8', connectTimeoutMs: 1000,
      readTimeoutMs: 3000, totalTimeoutMs: 5000, transportMetadata: null })
    const [createPath] = fetchMock.mock.calls[0] as unknown as [string, RequestInit]
    expect(createPath).toBe('/control/v1/business-integration/third-party-interfaces/7/transport-versions')
    await businessIntegrationApi.publishTransportVersion(7, 9)
    const [publishPath] = fetchMock.mock.calls[1] as unknown as [string, RequestInit]
    expect(publishPath).toBe('/control/v1/business-integration/third-party-interfaces/7/transport-versions/9:publish')
  })

  it('previews a selected channel and interface without sending secret material', async () => {
    const fetchMock = vi.fn(async () => new Response(JSON.stringify({ readiness: 'READY' }), {
      status: 200, headers: { 'Content-Type': 'application/json' }
    }))
    vi.stubGlobal('fetch', fetchMock)
    await businessIntegrationApi.requestPreview(3, 7, 11, 13)
    const [path, init] = fetchMock.mock.calls[0] as unknown as [string, RequestInit]
    expect(path).toBe('/control/v1/business-integration/channels/3/interfaces/7/request-preview?transportVersionId=11&authenticationVersionId=13')
    expect(init.body).toBeUndefined()
  })
})

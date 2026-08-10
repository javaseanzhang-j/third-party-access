import { accessServiceApi } from './accessServiceApi'

describe('access service product api', () => {
  afterEach(() => vi.unstubAllGlobals())
  it('creates the service and both standard contracts in one product command', async () => {
    const input = { serviceCode: 'sms.send', serviceName: '发送短信', description: null,
      invocationMode: 'SYNC' as const, idempotencyClass: 'IDEMPOTENT_WITH_KEY' as const,
      dataClassification: 'CONFIDENTIAL' as const, ownerCode: 'local',
      requestSchema: { type: 'object' }, requestExample: { mobile: '138****0000' },
      responseSchema: { type: 'object' }, responseExample: { accepted: true } }
    const fetchMock = vi.fn(async () => new Response(JSON.stringify({ id: 7, ...input, contracts: [], targets: [] }), {
      status: 201, headers: { 'Content-Type': 'application/json' }
    }))
    vi.stubGlobal('fetch', fetchMock)
    await accessServiceApi.create(input)
    const [path, init] = fetchMock.mock.calls[0] as unknown as [string, RequestInit]
    expect(path).toBe('/control/v1/product-model/services')
    expect(init.method).toBe('POST')
    expect(JSON.parse(String(init.body))).toMatchObject({ serviceCode: 'sms.send', requestSchema: { type: 'object' } })
  })
  it('adds a third-party interface as an adapter target', async () => {
    const fetchMock = vi.fn(async () => new Response(JSON.stringify({ bindingId: 9 }), {
      status: 201, headers: { 'Content-Type': 'application/json' }
    }))
    vi.stubGlobal('fetch', fetchMock)
    await accessServiceApi.addTarget(7, { providerContractId: 3, targetName: '阿里云短信实现', ownerCode: 'local' })
    const [path] = fetchMock.mock.calls[0] as unknown as [string, RequestInit]
    expect(path).toBe('/control/v1/product-model/services/7/targets')
  })
  it('reads the product-facing readiness result', async () => {
    const fetchMock = vi.fn(async () => new Response(JSON.stringify({ serviceId: 7, status: 'READY' }), {
      status: 200, headers: { 'Content-Type': 'application/json' }
    }))
    vi.stubGlobal('fetch', fetchMock)
    await accessServiceApi.readiness(7)
    const [path] = fetchMock.mock.calls[0] as unknown as [string, RequestInit]
    expect(path).toBe('/control/v1/product-model/services/7/readiness')
  })
  it('provisions mappings, channel and published binding version in one command', async () => {
    const fetchMock = vi.fn(async () => new Response(JSON.stringify({ bindingVersionId: 19 }), {
      status: 201, headers: { 'Content-Type': 'application/json' }
    }))
    vi.stubGlobal('fetch', fetchMock)
    await accessServiceApi.provisionTarget(7, { providerContractId: 3, providerContractVersionId: 4,
      accessChannelId: 5, endpointId: 6, targetName: '阿里云短信实现', ownerCode: 'local',
      requestMappings: [{ sourcePath: '$.mobile', targetPath: '$.phone', targetType: 'STRING', required: true }],
      responseMappings: [{ sourcePath: '$.ok', targetPath: '$.accepted', targetType: 'BOOLEAN', required: true }],
      authentication: { mode: 'CHANNEL_PARAMETERS', credentialRefId: null, headerName: null, prefix: null,
        sourceTemplate: null, encoding: null } })
    const [path, init] = fetchMock.mock.calls[0] as unknown as [string, RequestInit]
    expect(path).toBe('/control/v1/product-model/services/7/targets:provision')
    expect(JSON.parse(String(init.body))).toMatchObject({ accessChannelId: 5, endpointId: 6 })
  })
  it('provisions the business target without exposing endpoint or duplicate authentication', async () => {
    const fetchMock = vi.fn(async () => new Response(JSON.stringify({ bindingVersionId: 20 }), {
      status: 201, headers: { 'Content-Type': 'application/json' }
    }))
    vi.stubGlobal('fetch', fetchMock)
    await accessServiceApi.provisionBusinessTarget(7, { providerContractId: 3, providerContractVersionId: 4,
      accessChannelId: 5, transportVersionId: 8, targetName: '阿里云短信实现', ownerCode: 'local',
      requestMappings: [{ sourcePath: '$.mobile', targetPath: '$.phone', targetType: 'STRING', required: true }],
      responseMappings: [{ sourcePath: '$.ok', targetPath: '$.accepted', targetType: 'BOOLEAN', required: true }] })
    const [path, init] = fetchMock.mock.calls[0] as unknown as [string, RequestInit]
    expect(path).toBe('/control/v1/product-model/services/7/targets:provision-business')
    const body = JSON.parse(String(init.body)) as Record<string, unknown>
    expect(body).toMatchObject({ accessChannelId: 5, transportVersionId: 8 })
    expect(body).not.toHaveProperty('endpointId')
    expect(body).not.toHaveProperty('authentication')
  })
})

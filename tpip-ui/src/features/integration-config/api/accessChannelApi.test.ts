import { accessChannelApi } from './accessChannelApi'

describe('access channel api', () => {
  afterEach(() => vi.unstubAllGlobals())

  it('creates a channel through product-model API', async () => {
    const input = { providerId: 1, providerProductId: 8, channelCode: 'aliyun.sms', channelName: '阿里云短信',
      baseUrl: 'https://dysmsapi.aliyuncs.com', credentialRefId: 2, description: null }
    const fetchMock = vi.fn(async () => new Response(JSON.stringify({ id: 3, ...input }), {
      status: 201, headers: { 'Content-Type': 'application/json' }
    }))
    vi.stubGlobal('fetch', fetchMock)
    await accessChannelApi.create(input)
    const [path, init] = fetchMock.mock.calls[0] as unknown as [string, RequestInit]
    expect(path).toBe('/control/v1/product-model/channels')
    expect(init.method).toBe('POST')
    expect(new Headers(init.headers).get('X-Operator')).toBe('local-ui')
  })

  it('loads product-scoped interfaces for cascading selectors', async () => {
    const fetchMock = vi.fn(async () => new Response(JSON.stringify([7, 9]), {
      status: 200, headers: { 'Content-Type': 'application/json' }
    }))
    vi.stubGlobal('fetch', fetchMock)
    await accessChannelApi.productInterfaceIds(8)
    const [path] = fetchMock.mock.calls[0] as unknown as [string, RequestInit]
    expect(path).toBe('/control/v1/product-model/provider-products/8/interfaces')
  })

  it('upserts an interface override without secret plaintext', async () => {
    const input = { scope: 'INTERFACE' as const, providerContractId: 9, parameterCode: 'appSecret',
      parameterName: '应用密钥', location: 'SIGNATURE' as const, source: 'SECRET_REF' as const,
      dataType: 'STRING' as const, value: null, sourceSelector: null, secretRefId: 7,
      overrideMode: 'REPLACE' as const, required: true, sensitive: true, callerOverridable: false,
      description: null }
    const fetchMock = vi.fn(async () => new Response(JSON.stringify({ id: 10, ...input }), {
      status: 200, headers: { 'Content-Type': 'application/json' }
    }))
    vi.stubGlobal('fetch', fetchMock)
    await accessChannelApi.upsertParameter(3, input)
    const [path, init] = fetchMock.mock.calls[0] as unknown as [string, RequestInit]
    expect(path).toBe('/control/v1/product-model/channels/3/parameters')
    expect(init.method).toBe('PUT')
    expect(String(init.body)).not.toContain('secretValue')
  })

  it('creates and publishes a scoped policy version', async () => {
    const fetchMock = vi.fn(async () => new Response(JSON.stringify({ id: 12, lifecycleStatus: 'DRAFT' }), {
      status: 201, headers: { 'Content-Type': 'application/json' }
    }))
    vi.stubGlobal('fetch', fetchMock)
    await accessChannelApi.createPolicyVersion(3, { scope: 'INTERFACE', providerContractId: 9,
      policyName: '接口签名覆盖', document: null, disabledStepIds: ['authentication'] })
    const [createPath] = fetchMock.mock.calls[0] as unknown as [string, RequestInit]
    expect(createPath).toBe('/control/v1/product-model/channels/3/policy-versions')
    await accessChannelApi.publishPolicyVersion(3, 12)
    const [publishPath] = fetchMock.mock.calls[1] as unknown as [string, RequestInit]
    expect(publishPath).toBe('/control/v1/product-model/channels/3/policy-versions/12:publish')
  })
})

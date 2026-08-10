import { globalImpactCreationApi } from './globalImpactCreationApi'

describe('global impact creation api', () => {
  afterEach(() => vi.unstubAllGlobals())

  it('loads versions for the selected governance policy', async () => {
    const response = [{ id: 8, policyId: 7, versionNo: 2, lifecycleStatus: 'DRAFT' }]
    const fetchMock = vi.fn(async () => new Response(JSON.stringify(response), {
      status: 200, headers: { 'Content-Type': 'application/json' }
    }))
    vi.stubGlobal('fetch', fetchMock)

    await expect(globalImpactCreationApi.versions(7)).resolves.toEqual(response)
    expect(fetchMock).toHaveBeenCalledWith('/control/v1/drift-governance-policies/7/versions',
      expect.objectContaining({ method: 'GET' }))
  })

  it('creates a task with operator identity and the evidence ttl', async () => {
    const response = { jobId: 'job-1', status: 'PENDING', rowVersion: 0 }
    const fetchMock = vi.fn(async () => new Response(JSON.stringify(response), {
      status: 200, headers: { 'Content-Type': 'application/json' }
    }))
    vi.stubGlobal('fetch', fetchMock)
    const input = { candidatePolicyId: 7, candidateVersionId: 8, ttlSeconds: 3600 }

    await expect(globalImpactCreationApi.create(input)).resolves.toEqual(response)

    const [path, init] = fetchMock.mock.calls[0] as unknown as [string, RequestInit]
    expect(path).toMatch(/global-governance-policy-impact-jobs$/)
    expect(init.method).toBe('POST')
    expect(init.body).toBe(JSON.stringify(input))
    expect(new Headers(init.headers).get('X-Operator')).toBe('local-ui')
  })
})

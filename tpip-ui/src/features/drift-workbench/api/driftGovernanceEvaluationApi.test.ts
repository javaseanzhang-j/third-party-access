import { getDueGovernanceExecutions, getGovernanceEvaluations, getGovernanceExecutions } from './driftGovernanceEvaluationApi'

describe('drift governance evaluation api', () => {
  afterEach(() => vi.unstubAllGlobals())

  it('reads a workspace-scoped evaluation page', async () => {
    const fetchMock = vi.fn(async () => new Response(JSON.stringify({ items: [] }), {
      status: 200, headers: { 'Content-Type': 'application/json' }
    }))
    vi.stubGlobal('fetch', fetchMock)
    await getGovernanceEvaluations(23, 2, 20)
    expect(fetchMock).toHaveBeenCalledWith(
      '/control/v1/verification-drift-workbench/governance-evaluations?workspaceId=23&page=2&size=20',
      expect.any(Object)
    )
  })

  it('reads the frozen execution ledger without materializing candidates', async () => {
    const fetchMock = vi.fn(async () => new Response('[]', {
      status: 200, headers: { 'Content-Type': 'application/json' }
    }))
    vi.stubGlobal('fetch', fetchMock)
    await getGovernanceExecutions(23)
    expect(fetchMock).toHaveBeenCalledWith(
      '/control/v1/verification-drift-workbench/governance-executions?workspaceId=23',
      expect.objectContaining({ method: 'GET' })
    )
  })

  it('reads workspace-scoped unreserved due candidates', async () => {
    const fetchMock = vi.fn(async () => new Response('[]', {
      status: 200, headers: { 'Content-Type': 'application/json' }
    }))
    vi.stubGlobal('fetch', fetchMock)
    await getDueGovernanceExecutions(23)
    expect(fetchMock).toHaveBeenCalledWith(
      '/control/v1/verification-drift-workbench/governance-executions:due?workspaceId=23&limit=100',
      expect.objectContaining({ method: 'GET' })
    )
  })
})

import { getDriftOperationEvidence } from './driftWorkbenchApi'

describe('drift operation evidence api', () => {
  afterEach(() => vi.unstubAllGlobals())

  it('encodes the command key and reads immutable operation evidence', async () => {
    const fetchMock = vi.fn(async () => new Response(JSON.stringify({
      commandKey: 'apply/key 1',
      workspaceId: 23,
      status: 'APPLIED',
      request: { items: [] },
      result: { items: [] }
    }), { status: 200, headers: { 'Content-Type': 'application/json' } }))
    vi.stubGlobal('fetch', fetchMock)

    const result = await getDriftOperationEvidence('apply/key 1')

    expect(fetchMock).toHaveBeenCalledWith(
      '/control/v1/verification-drift-workbench/governance-operations/apply%2Fkey%201',
      expect.any(Object)
    )
    expect(result.status).toBe('APPLIED')
  })
})

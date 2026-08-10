import { driftGovernanceCommandApi } from './driftGovernanceCommandApi'

describe('drift governance command api', () => {
  afterEach(() => vi.unstubAllGlobals())

  it('materializes a report and creates a draft batch with the local operator', async () => {
    const fetchMock = vi.fn(async (_input: RequestInfo | URL, _init?: RequestInit) => new Response('{}', {
      status: 200, headers: { 'Content-Type': 'application/json' }
    }))
    vi.stubGlobal('fetch', fetchMock)
    await driftGovernanceCommandApi.materialize(23, 7)
    await driftGovernanceCommandApi.createBatch(23, 'local', [11, 12])
    expect(fetchMock).toHaveBeenNthCalledWith(1,
      '/control/v1/verification-drift-workbench/governance-executions:materialize', expect.any(Object))
    expect(fetchMock).toHaveBeenNthCalledWith(2,
      '/control/v1/verification-drift-workbench/governance-reminder-batches', expect.any(Object))
    const materialize = fetchMock.mock.calls[0]![1] as RequestInit
    const create = fetchMock.mock.calls[1]![1] as RequestInit
    expect(new Headers(materialize.headers).get('X-Operator')).toBe('local-ui')
    expect(materialize.body).toBe('{"workspaceId":23,"reportId":7}')
    expect(create.body).toContain('"executionIds":[11,12]')
  })

  it('sends rowVersion, reasons and explicit dispatch commands', async () => {
    const fetchMock = vi.fn(async (_input: RequestInfo | URL, _init?: RequestInit) => new Response('{}', {
      status: 200, headers: { 'Content-Type': 'application/json' }
    }))
    vi.stubGlobal('fetch', fetchMock)
    await driftGovernanceCommandApi.approveBatch(9, 2)
    await driftGovernanceCommandApi.cancelBatch(9, 3, 'obsolete')
    await driftGovernanceCommandApi.replaceBatch(9, 3, 'adjust members', 'local', [11])
    await driftGovernanceCommandApi.dispatchBatch(10, 4)
    expect(fetchMock.mock.calls.map(call => call[0])).toEqual([
      '/control/v1/verification-drift-workbench/governance-reminder-batches/9:approve',
      '/control/v1/verification-drift-workbench/governance-reminder-batches/9:cancel',
      '/control/v1/verification-drift-workbench/governance-reminder-batches/9:replace',
      '/control/v1/verification-drift-workbench/governance-reminder-batches/10:dispatch'
    ])
    expect((fetchMock.mock.calls[1]![1] as RequestInit).body).toBe('{"rowVersion":3,"reason":"obsolete"}')
    expect((fetchMock.mock.calls[2]![1] as RequestInit).body).toContain('"executionIds":[11]')
    expect((fetchMock.mock.calls[3]![1] as RequestInit).body).toBe('{"rowVersion":4}')
  })
})

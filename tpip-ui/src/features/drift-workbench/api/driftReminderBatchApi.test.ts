import { driftReminderBatchApi } from './driftReminderBatchApi'

describe('drift reminder batch api', () => {
  afterEach(() => vi.unstubAllGlobals())

  it('reads workspace batches and metrics without issuing commands', async () => {
    const fetchMock = vi.fn(async (_input: RequestInfo | URL, _init?: RequestInit) => new Response('[]', {
      status: 200, headers: { 'Content-Type': 'application/json' }
    }))
    vi.stubGlobal('fetch', fetchMock)
    await driftReminderBatchApi.batches(23)
    await driftReminderBatchApi.metrics(23)
    expect(fetchMock.mock.calls.map(call => call[0])).toEqual([
      '/control/v1/verification-drift-workbench/governance-reminder-batches?workspaceId=23',
      '/control/v1/verification-drift-workbench/governance-reminder-metrics?workspaceId=23'
    ])
    expect(fetchMock.mock.calls.every(call => (call[1] as RequestInit).method === 'GET')).toBe(true)
  })

  it('reads detail, replacement diff, delivery and immutable audit timeline projections', async () => {
    const fetchMock = vi.fn(async (_input: RequestInfo | URL, _init?: RequestInit) => new Response('{}', {
      status: 200, headers: { 'Content-Type': 'application/json' }
    }))
    vi.stubGlobal('fetch', fetchMock)
    await driftReminderBatchApi.detail(7)
    await driftReminderBatchApi.diff(7)
    await driftReminderBatchApi.delivery(7)
    await driftReminderBatchApi.timeline(7)
    expect(fetchMock.mock.calls.map(call => call[0])).toEqual([
      '/control/v1/verification-drift-workbench/governance-reminder-batches/7',
      '/control/v1/verification-drift-workbench/governance-reminder-batches/7/diff',
      '/control/v1/verification-drift-workbench/governance-reminder-batches/7/delivery-status',
      '/control/v1/verification-drift-workbench/governance-reminder-batches/7/timeline'
    ])
  })
})

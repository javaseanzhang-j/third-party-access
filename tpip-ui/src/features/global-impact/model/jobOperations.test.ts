import type { JobDetail, WorkspaceImpactSummary } from '../api/globalImpactApi'
import { expiryInsight, sealChecks } from './jobOperations'

describe('global impact job operations presentation', () => {
  it('raises warning and critical expiry windows only for mutable jobs', () => {
    const now = Date.parse('2026-08-09T08:00:00Z')
    expect(expiryInsight('2026-08-09T08:10:00Z', 'RUNNING', now).severity).toBe('warning')
    expect(expiryInsight('2026-08-09T08:04:00Z', 'READY', now).severity).toBe('error')
    expect(expiryInsight('2026-08-09T07:59:00Z', 'FAILED', now).remainingSeconds).toBe(0)
    expect(expiryInsight('2026-08-09T08:04:00Z', 'SEALED', now).visible).toBe(false)
  })

  it('requires server capability, complete success and a valid evidence window before sealing', () => {
    const detail = {
      summary: {
        status: 'READY', expired: false,
        allowedActions: [{ action: 'SEAL', enabled: true, disabledReasonCode: null }]
      }
    } as unknown as JobDetail
    const summary = {
      workspaceCount: 10,
      statuses: { pending: 0, running: 0, succeeded: 10, failed: 0 }
    } as WorkspaceImpactSummary

    expect(sealChecks(detail, summary).every(check => check.passed)).toBe(true)
    summary.statuses.failed = 1
    expect(sealChecks(detail, summary).find(check => check.code === 'NO_FAILED')?.passed).toBe(false)
  })
})

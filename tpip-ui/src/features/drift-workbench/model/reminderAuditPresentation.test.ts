import { auditEventLabel, auditTimelineType } from './reminderAuditPresentation'

describe('reminder audit presentation', () => {
  it('presents governed batch commands without hiding unknown immutable events', () => {
    expect(auditEventLabel('DRIFT_GOVERNANCE_REMINDER_BATCH_CREATED')).toBe('创建 DRAFT')
    expect(auditTimelineType('DRIFT_GOVERNANCE_REMINDER_BATCH_DISPATCHED')).toBe('danger')
    expect(auditEventLabel('FUTURE_EVENT')).toBe('FUTURE_EVENT')
    expect(auditTimelineType('FUTURE_EVENT')).toBe('primary')
  })
})

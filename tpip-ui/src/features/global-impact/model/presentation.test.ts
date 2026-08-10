import { formatTime, jobStatusLabel, shortId, statusType } from './presentation'

describe('global impact presentation', () => {
  it('maps stable backend states without changing their semantics', () => {
    expect(jobStatusLabel.READY).toBe('等待封板')
    expect(statusType('FAILED')).toBe('danger')
    expect(statusType('SEALED')).toBe('success')
  })

  it('formats identifiers and missing timestamps safely', () => {
    expect(shortId('b7eb1e02-b4ad-4c55-9276-019cc377e5f6')).toBe('b7eb1e02…e5f6')
    expect(formatTime(null)).toBe('—')
  })
})

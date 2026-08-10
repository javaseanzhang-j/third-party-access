export type AuditTimelineType = 'primary' | 'success' | 'warning' | 'danger' | 'info'

const labels: Record<string, string> = {
  DRIFT_GOVERNANCE_REMINDER_BATCH_CREATED: '创建 DRAFT',
  DRIFT_GOVERNANCE_REMINDER_BATCH_APPROVED: '批准批次',
  DRIFT_GOVERNANCE_REMINDER_BATCH_CANCELLED: '取消批次',
  DRIFT_GOVERNANCE_REMINDER_BATCH_REPLACEMENT_LINKED: '建立替代血缘',
  DRIFT_GOVERNANCE_REMINDER_BATCH_DISPATCHED: '提交 Outbox'
}

export function auditEventLabel(eventType: string): string {
  return labels[eventType] ?? eventType
}

export function auditTimelineType(eventType: string): AuditTimelineType {
  if (eventType.endsWith('_DISPATCHED')) return 'danger'
  if (eventType.endsWith('_APPROVED')) return 'success'
  if (eventType.endsWith('_CANCELLED')) return 'warning'
  if (eventType.endsWith('_REPLACEMENT_LINKED')) return 'info'
  return 'primary'
}

import { getJson } from '@/api/http'

const base = '/control/v1/verification-drift-workbench'
export type BatchStatus = 'DRAFT' | 'APPROVED' | 'DISPATCHED' | 'CANCELLED'
export interface ReminderBatch {
  id: number
  batchCode: string
  workspaceId: number
  aggregationKey: string
  environmentCode: string
  ownerCode: string
  creationSource: 'MANUAL' | 'AUTOMATION'
  status: BatchStatus
  memberCount: number
  contentChecksum: string
  rowVersion: number
  outboxId: number | null
  replacesBatchId: number | null
  replacedByBatchId: number | null
  createdBy: string
  createdAt: string
  approvedBy: string | null
  approvedAt: string | null
  cancelReason: string | null
  cancelledBy: string | null
  cancelledAt: string | null
  dispatchedBy: string | null
  dispatchedAt: string | null
}
export interface BatchMember { executionId: number; reminderNo: number; evaluationChecksum: string }
export interface BatchDetail { batch: ReminderBatch; members: BatchMember[] }
export interface MemberChange {
  executionId: number
  changeKind: 'ADDED' | 'REMOVED' | 'UNCHANGED' | 'MODIFIED'
  previousReminderNo: number | null
  currentReminderNo: number | null
  previousEvaluationChecksum: string | null
  currentEvaluationChecksum: string | null
}
export interface BatchDiff {
  fromBatchId: number
  toBatchId: number
  addedCount: number
  removedCount: number
  unchangedCount: number
  modifiedCount: number
  members: MemberChange[]
}
export interface ChannelDelivery {
  deliveryId: number
  channelCode: string
  status: 'PENDING' | 'CLAIMED' | 'DELIVERED' | 'DEAD_LETTER'
  attemptCount: number
  deliveredAt: string | null
  deadLetteredAt: string | null
}
export interface DeliveryOverview {
  outboxId: number
  outboxStatus: string
  routingStatus: 'UNROUTED' | 'ROUTED' | 'NO_MATCH' | 'RENDER_FAILED'
  routingAttemptedAt: string | null
  deliveredAt: string | null
  createdAt: string
  deliveries: ChannelDelivery[]
}
export interface DeliveryStatus { batchId: number; submitted: boolean; overview: DeliveryOverview | null }
export interface BatchAuditEvent {
  eventId: string
  eventType: string
  actorCode: string
  summary: string
  rowVersion: number | null
  status: BatchStatus | null
  reason: string | null
  replacesBatchId: number | null
  replacedByBatchId: number | null
  outboxId: number | null
  occurredAt: string
}
export interface BatchTimeline {
  batchId: number
  batchCode: string
  currentRowVersion: number
  events: BatchAuditEvent[]
}
export interface ReminderMetrics {
  totalBatches: number
  draftBatches: number
  approvedBatches: number
  dispatchedBatches: number
  cancelledBatches: number
  dueUnbatchedExecutions: number
  activeReservedExecutions: number
  oldestDueAt: string | null
  unroutedOutboxes: number
  routedOutboxes: number
  noMatchOutboxes: number
  renderFailedOutboxes: number
  pendingDeliveries: number
  claimedDeliveries: number
  deliveredDeliveries: number
  deadLetterDeliveries: number
  terminalDeliverySuccessRate: number
}

export const driftReminderBatchApi = {
  batches: (workspaceId: number, signal?: AbortSignal) =>
    getJson<ReminderBatch[]>(`${base}/governance-reminder-batches?workspaceId=${workspaceId}`, signal),
  detail: (batchId: number, signal?: AbortSignal) =>
    getJson<BatchDetail>(`${base}/governance-reminder-batches/${batchId}`, signal),
  diff: (batchId: number, signal?: AbortSignal) =>
    getJson<BatchDiff>(`${base}/governance-reminder-batches/${batchId}/diff`, signal),
  delivery: (batchId: number, signal?: AbortSignal) =>
    getJson<DeliveryStatus>(`${base}/governance-reminder-batches/${batchId}/delivery-status`, signal),
  timeline: (batchId: number, signal?: AbortSignal) =>
    getJson<BatchTimeline>(`${base}/governance-reminder-batches/${batchId}/timeline`, signal),
  metrics: (workspaceId: number, signal?: AbortSignal) =>
    getJson<ReminderMetrics>(`${base}/governance-reminder-metrics?workspaceId=${workspaceId}`, signal)
}

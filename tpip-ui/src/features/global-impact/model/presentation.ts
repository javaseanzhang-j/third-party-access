import type { JobPriority, JobStatus, ItemStatus, RiskLevel } from '../api/globalImpactApi'

export const jobStatusLabel: Record<JobStatus, string> = {
  PENDING: '等待执行', RUNNING: '执行中', FAILED: '执行失败', READY: '等待封板',
  SEALED: '已封板', EXPIRED: '已过期', CANCELLED: '已取消'
}
export const itemStatusLabel: Record<ItemStatus, string> = {
  PENDING: '等待执行', RUNNING: '执行中', SUCCEEDED: '成功', FAILED: '失败'
}
export const priorityLabel: Record<JobPriority, string> = {
  LOW: '低', NORMAL: '普通', HIGH: '高', CRITICAL: '紧急'
}
export const riskLabel: Record<RiskLevel, string> = {
  LOW: '低风险', MEDIUM: '中风险', HIGH: '高风险', CRITICAL: '关键风险'
}
export const actionLabel: Record<string, string> = {
  REPRIORITIZE: '调整优先级', CANCEL: '取消任务', RETRY_FAILED: '重试失败项',
  SEAL: '封板快照', VIEW_SEALED_SNAPSHOT: '查看封板快照'
}
export const recoveryLabel: Record<string, string> = {
  READY_FOR_MANUAL_SEAL: '计算完成，等待人工封板', REVIEW_FAILURE_AND_RETRY: '评审失败项后重试',
  NO_ACTION_TERMINAL: '任务已终止，无需恢复', START_OR_ENABLE_WORKER: '启动或启用 Worker',
  WAIT_FOR_ACTIVE_WORKER: 'Worker 正在执行', REDISPATCH_AFTER_LEASE_EXPIRY: '租约到期后重新调度'
}
export function statusType(status: JobStatus | ItemStatus): 'success' | 'warning' | 'danger' | 'info' | 'primary' {
  if (status === 'SUCCEEDED' || status === 'SEALED' || status === 'READY') return 'success'
  if (status === 'FAILED') return 'danger'
  if (status === 'RUNNING') return 'primary'
  if (status === 'PENDING') return 'warning'
  return 'info'
}
export function formatTime(value: string | null | undefined): string {
  if (!value) return '—'
  return new Intl.DateTimeFormat('zh-CN', { dateStyle: 'medium', timeStyle: 'medium', hour12: false }).format(new Date(value))
}
export function shortId(value: string): string { return value.length > 14 ? `${value.slice(0, 8)}…${value.slice(-4)}` : value }

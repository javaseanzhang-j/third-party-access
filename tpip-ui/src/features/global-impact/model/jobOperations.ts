import type { JobDetail, WorkspaceImpactSummary } from '../api/globalImpactApi'

const activeStatuses = new Set(['PENDING', 'RUNNING', 'FAILED', 'READY'])

export interface ExpiryInsight {
  visible: boolean; severity: 'warning' | 'error'; title: string; remainingSeconds: number
}

export function expiryInsight(expiresAt: string, status: string, nowMillis = Date.now()): ExpiryInsight {
  const seconds = Math.floor((new Date(expiresAt).getTime() - nowMillis) / 1000)
  if (!activeStatuses.has(status) || seconds > 900)
    return { visible: false, severity: 'warning', title: '', remainingSeconds: Math.max(0, seconds) }
  if (seconds <= 0)
    return { visible: true, severity: 'error', title: '证据窗口已经到期，任务不能继续计算、重试或封板。', remainingSeconds: 0 }
  if (seconds <= 300)
    return { visible: true, severity: 'error', title: `证据将在 ${Math.ceil(seconds / 60)} 分钟内到期，请立即完成评审或重新创建任务。`, remainingSeconds: seconds }
  return { visible: true, severity: 'warning', title: `证据将在 ${Math.ceil(seconds / 60)} 分钟内到期，请预留计算与封板时间。`, remainingSeconds: seconds }
}

export interface SealCheck { code: string; label: string; passed: boolean }

export function sealChecks(detail: JobDetail, summary: WorkspaceImpactSummary): SealCheck[] {
  const sealEnabled = detail.summary.allowedActions.find(action => action.action === 'SEAL')?.enabled === true
  return [
    { code: 'READY', label: '任务状态为 READY', passed: detail.summary.status === 'READY' },
    { code: 'ALL_SUCCEEDED', label: '全部 Workspace 已成功生成证据', passed: summary.statuses.succeeded === summary.workspaceCount },
    { code: 'NO_FAILED', label: '不存在失败 Workspace', passed: summary.statuses.failed === 0 },
    { code: 'NOT_EXPIRED', label: '证据仍在有效期内', passed: !detail.summary.expired },
    { code: 'SEAL_ALLOWED', label: '服务端允许使用当前 Row Version 封板', passed: sealEnabled }
  ]
}

export const recoveryGuidance: Record<string, { title: string; description: string }> = {
  START_OR_ENABLE_WORKER: {
    title: '等待 Worker 接管', description: '确认独立 Worker 已启用，并与 Control Plane 使用一致的自动化令牌和地址。'
  },
  WAIT_FOR_ACTIVE_WORKER: {
    title: 'Worker 正在处理', description: '当前存在有效调度租约，请等待下一批进度；不要从 UI 模拟 Worker 请求。'
  },
  REDISPATCH_AFTER_LEASE_EXPIRY: {
    title: '等待租约恢复调度', description: '现有租约已无有效进展，检查 Worker 日志和调度配置，租约到期后可重新接管。'
  },
  REVIEW_FAILURE_AND_RETRY: {
    title: '评审失败 Workspace', description: '先查看失败分类与安全错误摘要；原因修复后再显式重试失败项。'
  },
  READY_FOR_MANUAL_SEAL: {
    title: '等待人工封板', description: '计算已经完成，请核对覆盖、失败数、有效期和影响摘要后执行封板。'
  },
  NO_ACTION_TERMINAL: {
    title: '任务已进入终态', description: '无需运行恢复操作；已封板任务可继续查看不可变快照与审计证据。'
  }
}

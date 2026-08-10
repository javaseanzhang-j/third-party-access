import { ApiError } from '@/api/http'

export type JobCommandAction = 'REPRIORITIZE' | 'CANCEL' | 'RETRY_FAILED' | 'SEAL'

export const commandTitle: Record<JobCommandAction, string> = {
  REPRIORITIZE: '调整任务优先级', CANCEL: '取消全局影响任务',
  RETRY_FAILED: '重试失败 Workspace', SEAL: '封板全局影响快照'
}
export const commandSuccess: Record<JobCommandAction, string> = {
  REPRIORITIZE: '任务优先级已更新', CANCEL: '任务已取消',
  RETRY_FAILED: '失败 Workspace 已重新进入队列', SEAL: '全局影响快照已封板'
}

export function commandErrorMessage(error: unknown): string {
  if (error instanceof ApiError && error.code === 'TPIP_GLOBAL_IMPACT_JOB_COMMAND_CONFLICT') {
    return '任务状态或版本已经变化，页面已刷新，请根据最新状态重新操作。'
  }
  if (error instanceof ApiError && error.status === 400) return `请求未通过校验：${error.message}`
  if (error instanceof ApiError) return `操作失败（${error.code}）：${error.message}`
  return '操作失败，请检查 Control Plane 状态后重试。'
}

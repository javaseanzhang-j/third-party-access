import { postJson } from '@/api/http'
import type { JobPriority, JobStatus } from './globalImpactApi'

export interface JobCommandResult { jobId: string; status: JobStatus; rowVersion: number; sealedSnapshotId?: string | null }
export interface ReprioritizeResult { job: JobCommandResult; priority: JobPriority }

const base = '/control/v1/verification-drift-workbench/global-governance-policy-impact-jobs'
const operator = (import.meta.env.VITE_TPIP_OPERATOR as string | undefined)?.trim() || 'local-ui'
const headers = { 'X-Operator': operator }
const target = (jobId: string, operation: string) => `${base}/${encodeURIComponent(jobId)}:${operation}`

export const globalImpactCommandApi = {
  reprioritize: (jobId: string, rowVersion: number, priority: JobPriority, reason: string) =>
    postJson<ReprioritizeResult>(target(jobId, 'reprioritize'), { rowVersion, priority, reason }, headers),
  cancel: (jobId: string, rowVersion: number, reason: string) =>
    postJson<JobCommandResult>(target(jobId, 'cancel'), { rowVersion, reason }, headers),
  retryFailed: (jobId: string, rowVersion: number, reason: string) =>
    postJson<JobCommandResult>(target(jobId, 'retry-failed'), { rowVersion, reason }, headers),
  seal: (jobId: string, rowVersion: number) =>
    postJson<JobCommandResult>(target(jobId, 'seal'), { rowVersion }, headers)
}

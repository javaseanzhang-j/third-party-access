import { getJson } from '@/api/http'
import type { DriftKind, Scope } from './driftWorkbenchApi'

const base = '/control/v1/verification-drift-workbench'

export interface SlaEvidence {
  mode: 'POLICY' | 'EXPLICIT_ANALYSIS_OVERRIDE'
  source: 'WORKSPACE_POLICY' | 'GLOBAL_POLICY' | 'BUILT_IN_DEFAULT'
  policyId: number | null
  policyVersionId: number | null
  configuredSlaSeconds: number
  effectiveSlaSeconds: number
  ownerCode: string | null
}
export interface BacklogMetrics {
  actionableReports: number
  withinSlaReports: number
  breachedReports: number
  assignedReports: number
  unassignedReports: number
  unassignedBreachedReports: number
  breachRatePercent: number
  assignmentCoveragePercent: number
  oldestActionableAt: string | null
  oldestAgeHours: number
  averageAgeHours: number
}
export interface ResolutionMetrics {
  resolvedReports: number
  withinSlaReports: number
  breachedReports: number
  acceptedReports: number
  dismissedReports: number
  slaCompliancePercent: number
  averageResolutionHours: number
  maximumResolutionHours: number
}
export interface CommandMetrics {
  commands: number
  previewedCommands: number
  appliedCommands: number
  rejectedCommands: number
  executionSuccessPercent: number
  requestedItems: number
  eligibleItems: number
  appliedItems: number
  rejectedItems: number
}
export interface AssigneeWorkload {
  assigneeCode: string | null
  assigned: boolean
  actionableReports: number
  overdueReports: number
  oldestActionableAt: string | null
  oldestAgeHours: number
}
export interface DailyMetric {
  date: string
  createdReports: number
  resolvedReports: number
  previewedCommands: number
  appliedCommands: number
  rejectedCommands: number
}
export interface GovernanceMetrics {
  workspaceId: number
  snapshotAt: string
  windowStart: string
  windowDays: number
  slaHours: number
  slaSeconds: number
  slaPolicy: SlaEvidence
  backlog: BacklogMetrics
  resolution: ResolutionMetrics
  commands: CommandMetrics
  assignees: AssigneeWorkload[]
  daily: DailyMetric[]
}
export interface DriftGroup {
  checkCode: string
  driftKind: DriftKind
  reportCount: number
  actionableReportCount: number
  overdueReportCount: number
  affectedWorkspaceCount: number
  latestReportAt: string
}

export interface MetricsQuery { workspaceId: number; windowDays: number; slaHours?: number }
export interface GroupsQuery { workspaceId: number; scope?: Scope; overdueAfterHours: number; limit?: number }

export function getGovernanceMetrics(query: MetricsQuery, signal?: AbortSignal): Promise<GovernanceMetrics> {
  const values = new URLSearchParams({ workspaceId: String(query.workspaceId), windowDays: String(query.windowDays) })
  if (query.slaHours) values.set('slaHours', String(query.slaHours))
  return getJson<GovernanceMetrics>(`${base}/governance-metrics?${values}`, signal)
}

export function getDriftGroups(query: GroupsQuery, signal?: AbortSignal): Promise<DriftGroup[]> {
  const values = new URLSearchParams({
    workspaceId: String(query.workspaceId),
    scope: query.scope ?? 'ALL',
    overdueOnly: 'false',
    overdueAfterHours: String(query.overdueAfterHours),
    limit: String(query.limit ?? 20)
  })
  return getJson<DriftGroup[]>(`${base}/groups?${values}`, signal)
}
